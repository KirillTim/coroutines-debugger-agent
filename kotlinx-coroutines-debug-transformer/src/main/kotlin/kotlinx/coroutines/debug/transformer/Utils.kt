package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.*
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue
import org.jetbrains.org.objectweb.asm.tree.analysis.SimpleVerifier
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter

/**
 * @author Kirill Timofeev
 */

internal val OBJECT_TYPE = Type.getType(Any::class.java)
internal val THROWABLE_TYPE = Type.getType(Throwable::class.java)
internal val STRING_TYPE = Type.getType(String::class.java)
internal val CONTINUATION_TYPE = Type.getType("Lkotlin/coroutines/experimental/Continuation;")
internal val COROUTINE_IMPL_TYPE = Type.getType("Lkotlin/coroutines/experimental/jvm/internal/CoroutineImpl;")

internal val AbstractInsnNode?.isGetCOROUTINE_SUSPENDED: Boolean
    get() = this is MethodInsnNode && name == "getCOROUTINE_SUSPENDED"
            && owner == "kotlin/coroutines/experimental/intrinsics/IntrinsicsKt"
            && desc == "()${OBJECT_TYPE.descriptor}"

internal val AbstractInsnNode?.isSetLabel: Boolean
    get() = this is FieldInsnNode && (opcode == Opcodes.PUTSTATIC || opcode == Opcodes.PUTFIELD) && name == "label"

internal val AbstractInsnNode?.isGetLabel: Boolean
    get() = this is FieldInsnNode && (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETFIELD) && name == "label"

internal val AbstractInsnNode?.isARETURN: Boolean
    get() = this != null && opcode == Opcodes.ARETURN

internal val AbstractInsnNode.isMeaningful: Boolean
    get() = when (type) {
        AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME -> false
        else -> true
    }

internal val AbstractInsnNode.nextMeaningful: AbstractInsnNode?
    get() {
        var cur = next
        while (cur != null && !cur.isMeaningful)
            cur = cur.next
        return cur
    }

internal val AbstractInsnNode.previousMeaningful: AbstractInsnNode?
    get() {
        var cur = previous
        while (cur != null && !cur.isMeaningful)
            cur = cur.previous
        return cur
    }

internal fun AbstractInsnNode?.isASTORE() = this != null && opcode == Opcodes.ASTORE
internal fun AbstractInsnNode?.isALOAD(operand: Int? = null) =
        this != null && opcode == Opcodes.ALOAD && (operand == null || (this is VarInsnNode && `var` == operand))

internal inline fun AbstractInsnNode?.nextMatches(predicate: (AbstractInsnNode) -> Boolean) =
        this?.nextMeaningful?.takeIf(predicate)

internal inline fun AbstractInsnNode?.previousMatches(predicate: (AbstractInsnNode) -> Boolean) =
        this?.previousMeaningful?.takeIf(predicate)

internal fun AbstractInsnNode?.isStateMachineStartsHere() = //TODO check variable types and indexes
        isGetCOROUTINE_SUSPENDED
                && nextMatches { it.isASTORE() }
                .nextMatches { it.isALOAD() }
                .nextMatches { it.isGetLabel }
                .nextMatches { it is TableSwitchInsnNode } != null

internal fun MethodNode.isStateMachineForAnonymousSuspendFunction() =
        isDoResume && instructions[0].isStateMachineStartsHere()

internal fun InsnList.lastMeaningful() = if (last.isMeaningful) last else last.previousMeaningful

internal fun InsnList.lastARETURN(): AbstractInsnNode? {
    var cur = lastMeaningful()
    while (cur != null && !cur.isARETURN)
        cur = cur.previous
    return cur
}

internal val InsnList.sequence: Sequence<AbstractInsnNode>
    get() = iterator().asSequence().filterIsInstance<AbstractInsnNode>()

internal fun MethodNode.correspondingSuspendFunctionForDoResume(): SuspendFunction {
    require(isDoResume) { "$name should be doResume functionCall" }
    val last = instructions.lastMeaningful()
    require(last.isARETURN) { "last meaningful instruction must be areturn" }
    val suspendFunCall = requireNotNull(last?.previousMeaningful as? MethodInsnNode,
            { "can't find corresponding suspend method call" })
    return NamedSuspendFunction(MethodId.build(suspendFunCall.name, suspendFunCall.owner, suspendFunCall.desc))
}

internal val Type.isResumeMethodDesc: Boolean
    get() = returnType == OBJECT_TYPE && argumentTypes.contentEquals(arrayOf(OBJECT_TYPE, THROWABLE_TYPE))

internal val MethodNode.isAbstract: Boolean
    get() = access and Opcodes.ACC_ABSTRACT != 0

internal val MethodNode.isBridge: Boolean
    get() = access and Opcodes.ACC_BRIDGE != 0

internal val MethodNode.isDoResume: Boolean
    get() = name == "doResume" && Type.getType(desc).isResumeMethodDesc && !isAbstract

internal val Type.isCreateCoroutineMethodDesc: Boolean
    get() = returnType == CONTINUATION_TYPE && argumentTypes.isNotEmpty() && argumentTypes.last() == CONTINUATION_TYPE

internal fun MethodNode.isCreateCoroutine(owner: ClassNode) =
        name == "create" && owner.name != COROUTINE_IMPL_TYPE.internalName //FIXME: handle create that now is bridge
                && Type.getType(desc).isCreateCoroutineMethodDesc && !isBridge && !isAbstract

internal fun MethodNode.isSuspend() = isSuspend(name, desc)

internal fun MethodNode.suspendCallInstructions(classNode: ClassNode): List<MethodInsnNode> {
    val suspensionCalls = mutableListOf<MethodInsnNode>()
    val functionInterfaceInvokes = mutableListOf<Pair<Int, MethodInsnNode>>()
    for ((index, instruction) in instructions.sequence.withIndex()) {
        if (instruction.isSuspendSignature())
            suspensionCalls += (instruction as MethodInsnNode)
        else if (instruction.isFunctionInterfaceInvoke)
            functionInterfaceInvokes += Pair(index, instruction as MethodInsnNode)
    }
    if (functionInterfaceInvokes.isNotEmpty()) {
        val expectedInvokeLastArgumentClassName = //dirty hack
                if (isDoResume
                        || classNode.superName == COROUTINE_IMPL_TYPE.internalName
                        || classNode.interfaces.contains(CONTINUATION_TYPE.internalName)) classNode.name
                else {
                    val fsm = instructions.sequence.firstOrNull { it.isStateMachineStartsHere() }
                    val getLabel = fsm?.nextMeaningful?.nextMeaningful?.nextMeaningful as? FieldInsnNode
                    getLabel?.owner
                }
        val stackAnalyzer = Analyzer(object : SimpleVerifier() {
            //getClass is called from isAssignableFrom(..), getSuperClass(..), isInterface(..),
            override fun getClass(t: Type?): Class<*>? = null

            override fun isInterface(t: Type?) = false
            override fun isAssignableFrom(t: Type?, u: Type?) = true
            override fun getSuperClass(t: Type?): Type = OBJECT_TYPE
        })
        stackAnalyzer.analyze(classNode.name, this)
        for ((index, invoke) in functionInterfaceInvokes) {
            val frame = stackAnalyzer.frames[index] ?: continue
            val lastMethodArgument = frame.getStack(frame.stackSize - 1) as BasicValue
            //while isAssignableFrom and getSuperClass do nothing, stack analyzer can't calculate types correctly
            //so we compare it with state machine class name
            if (lastMethodArgument.type == CONTINUATION_TYPE || lastMethodArgument.type == COROUTINE_IMPL_TYPE
                    || lastMethodArgument.type.internalName == expectedInvokeLastArgumentClassName)
                suspensionCalls += invoke
        }
    }
    return suspensionCalls
}

internal val AbstractInsnNode.isFunctionInterfaceInvoke: Boolean
    get() = this is MethodInsnNode && opcode == Opcodes.INVOKEINTERFACE && name == "invoke"
            && owner.matches("kotlin/jvm/functions/Function(\\d+)".toRegex())

internal fun AbstractInsnNode.isSuspendSignature() = this is MethodInsnNode && isSuspend(name, desc)

internal fun continuationOffsetFromEndInDesc(name: String) = if (name.endsWith("\$default")) 3 else 1

private fun isSuspend(name: String, desc: String): Boolean {
    val offset = continuationOffsetFromEndInDesc(name)
    val descType = Type.getType(desc)
    return descType.argumentTypes.getOrNull(descType.argumentTypes.size - offset) == CONTINUATION_TYPE
            && descType.returnType == Type.getType(Any::class.java)
}

private fun prettyPrint(method: MethodId, argumentValues: List<Any>? = null): String {
    val descType = Type.getType(method.desc)
    val arguments = argumentValues?.joinToString() ?:
            descType.argumentTypes.joinToString(transform = { it.className.split('.').last() })
    val returnType = descType.returnType.className.split('.').last()
    return "${method.owner.replace('/', '.')}.${method.name}($arguments): $returnType"
}

internal fun MethodInsnNode.buildMethodId() = MethodId.build(name, owner, desc)

internal fun MethodNode.buildMethodId(classNode: ClassNode) = MethodId.build(name, classNode.name, desc)

internal fun MethodNode.buildMethodIdWithInfo(classNode: ClassNode): MethodIdWithInfo {
    val isSMForAnonymous = isStateMachineForAnonymousSuspendFunction() //FIXME how to determine is anonymous or not?
    val info = MethodInfo(isSMForAnonymous, isSuspend(), isDoResume, isSMForAnonymous)
    val methodId = buildMethodId(classNode)
    return MethodIdWithInfo(methodId, info, prettyPrint(methodId))
}

internal fun MethodNode.firstInstructionLineNumber() = //FIXME
        instructions.sequence.filterIsInstance<LineNumberNode>().map { it.line }.min() ?: -1

internal fun ClassNode.byteCodeString(): String {
    val writer = StringWriter()
    accept(TraceClassVisitor(PrintWriter(writer)))
    return writer.toString()
}

private fun InsnList.methodCallNodeToLabelNode(): Map<MethodInsnNode, LabelNode> {
    val map = mutableMapOf<MethodInsnNode, LabelNode>()
    var lastLabel: LabelNode? = null
    for (inst in this) {
        if (inst is MethodInsnNode && lastLabel != null) {
            map[inst] = lastLabel
        } else if (inst is LabelNode) {
            lastLabel = inst
        }
    }
    return map
}

private fun InsnList.labelNodeToLineNumber() =
        sequence.filterIsInstance<LineNumberNode>().map { it.start to it.line }.toMap()

/*
* It is possible not to have line number before method call (e.g: inside synthetic bridge)
* */
internal fun InsnList.methodCallLineNumber(): Map<MethodInsnNode, Int?> {
    val methodToLabel = methodCallNodeToLabelNode()
    val labelToLineNumber = labelNodeToLineNumber()
    return methodToLabel.map { it.key to labelToLineNumber[it.value] }.toMap()
}
