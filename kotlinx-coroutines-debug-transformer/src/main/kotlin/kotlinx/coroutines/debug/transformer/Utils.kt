package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import org.objectweb.asm.util.TraceClassVisitor
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

internal val AbstractInsnNode?.isGetLabel: Boolean
    get() = this is FieldInsnNode && name == "label"

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
internal fun AbstractInsnNode?.isALOAD(operand: Int? = null)
        = this != null && opcode == Opcodes.ALOAD && (operand == null || (this is VarInsnNode && `var` == operand))

internal inline fun AbstractInsnNode?.nextMatches(pred: (AbstractInsnNode) -> Boolean)
        = this?.nextMeaningful?.takeIf(pred)

internal fun MethodNode.isStateMachineForAnonymousSuspendFunction()
        = isDoResume() &&
        instructions[0]
                .takeIf { it.isGetCOROUTINE_SUSPENDED }
                .nextMatches { it.isASTORE() }
                .nextMatches { it.isALOAD(0) }
                .nextMatches { it.isGetLabel }
                .nextMatches { it is TableSwitchInsnNode } != null

internal fun InsnList.lastMeaningful()
        = if (last.isMeaningful) last else last.previousMeaningful

internal fun InsnList.lastARETURN(): AbstractInsnNode? {
    var cur = lastMeaningful()
    while (cur != null && !cur.isARETURN)
        cur = cur.previous
    return cur
}

internal fun MethodNode.correspondingSuspendFunctionForDoResume(): SuspendFunction {
    require(isDoResume()) { "${name} should be doResume functionCall" }
    val last = instructions.lastMeaningful()
    require(last.isARETURN) { "last meaningful instruction must be areturn" }
    val suspendFunCall = last?.previousMeaningful as? MethodInsnNode
    require(suspendFunCall?.opcode == Opcodes.INVOKESTATIC) { "can't find corresponding suspend function call" }
    return NamedSuspendFunction(MethodId(suspendFunCall!!.name, suspendFunCall.owner, suspendFunCall.desc))
}

internal fun Type.isResumeMethodDesc() =
        returnType == OBJECT_TYPE && argumentTypes.contentEquals(arrayOf(OBJECT_TYPE, THROWABLE_TYPE))

internal fun MethodNode.isDoResume() = name == "doResume"
        && Type.getType(desc).isResumeMethodDesc() && (access and Opcodes.ACC_ABSTRACT == 0)

internal fun MethodNode.isSuspend() = isSuspend(name, desc)

internal fun MethodInsnNode.isSuspend() = isSuspend(name, desc)

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

internal fun MethodInsnNode.buildMethodId()
        = MethodId(name, owner, desc)

internal fun MethodNode.buildMethodId(classNode: ClassNode)
        = MethodId(name, classNode.name, desc)

internal fun MethodNode.buildMethodIdWithInfo(classNode: ClassNode): MethodIdWithInfo {
    val isSMForAnonymous = isStateMachineForAnonymousSuspendFunction() //FIXME how to determine is anonymous or not?
    val info = MethodInfo(isSMForAnonymous, isSuspend(), isDoResume(), isSMForAnonymous)
    val methodId = buildMethodId(classNode)
    return MethodIdWithInfo(methodId, info, prettyPrint(methodId))
}

internal fun MethodNode.firstInstructionLineNumber() = //FIXME
        instructions.iterator().asSequence().filterIsInstance<LineNumberNode>().map { it.line }.min() ?: -1

internal fun ClassNode.byteCodeString(): String {
    val writer = StringWriter()
    accept(TraceClassVisitor(PrintWriter(writer)))
    return writer.toString()
}

private fun methodCallNodeToLabelNode(instructions: InsnList): Map<MethodInsnNode, LabelNode> {
    val map = mutableMapOf<MethodInsnNode, LabelNode>()
    var lastLabel: LabelNode? = null
    for (inst in instructions) {
        if (inst is MethodInsnNode && lastLabel != null) {
            map[inst] = lastLabel
        } else if (inst is LabelNode) {
            lastLabel = inst
        }
    }
    return map
}

private fun labelNodeToLineNumber(instructions: InsnList)
        = instructions.iterator().asSequence().filterIsInstance<LineNumberNode>().map { it.start to it.line }.toMap()

/*
* It is possible not to have line number before method call (e.g: inside synthetic bridge)
* */
internal fun methodCallLineNumber(instructions: InsnList): Map<MethodInsnNode, Int?> {
    val methodToLabel = methodCallNodeToLabelNode(instructions)
    val labelToLineNumber = labelNodeToLineNumber(instructions)
    return methodToLabel.map { it.key to labelToLineNumber[it.value] }.toMap()
}
