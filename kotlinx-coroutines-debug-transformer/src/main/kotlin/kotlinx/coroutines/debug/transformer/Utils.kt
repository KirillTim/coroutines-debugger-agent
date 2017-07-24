package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*

/**
 * @author Kirill Timofeev
 */

internal val OBJECT_TYPE = Type.getType(Any::class.java)
internal val THROWABLE_TYPE = Type.getType(Throwable::class.java)
internal val CONTINUATION_TYPE = Type.getType("Lkotlin/coroutines/experimental/Continuation;")
internal val COROUTINE_IMPL_TYPE = Type.getType("Lkotlin/coroutines/experimental/jvm/internal/CoroutineImpl;")

private fun isGetCOROUTINE_SUSPENDED(inst: AbstractInsnNode) =
        inst is MethodInsnNode && inst.name == "getCOROUTINE_SUSPENDED"
                && inst.owner == "kotlin/coroutines/experimental/intrinsics/IntrinsicsKt"
                && inst.desc == "()Ljava/lang/Object;"


internal fun isStateMachineForAnonymousSuspendFunction(method: MethodNode): Boolean { //FIXME
    if (!method.isDoResume()) {
        return false
    }
    val getSuspendedConst = method.instructions.get(0)
    val l0 = method.instructions.get(1)
    val getLabel = method.instructions.get(5)
    val tableSwitch = method.instructions.get(6)
    return isGetCOROUTINE_SUSPENDED(getSuspendedConst) && l0 is LabelNode
            && getLabel is FieldInsnNode && getLabel.name == "label" && tableSwitch is TableSwitchInsnNode
}

internal fun correspondingSuspendFunctionForDoResume(method: MethodNode): SuspendFunction { //FIXME
    if (!method.isDoResume()) {
        throw IllegalArgumentException("${method.name} should be doResume functionCall")
    }
    val aReturn = method.instructions.last.previous
    val suspendFunctionCall = aReturn.previous
    if (suspendFunctionCall is MethodInsnNode && suspendFunctionCall.opcode == Opcodes.INVOKESTATIC
            && aReturn is InsnNode && aReturn.opcode == Opcodes.ARETURN) {
        return NamedSuspendFunction(MethodIdSimple(suspendFunctionCall.name, suspendFunctionCall.owner, suspendFunctionCall.desc))
    }
    throw IllegalArgumentException("Unexpected end instructions in ${method.name}")
}

fun MethodNode.isDoResume(): Boolean {
    if (name != "doResume") return false
    val type = Type.getType(desc)
    return type.returnType == OBJECT_TYPE && type.argumentTypes.size == 2 // FIXME
            && type.argumentTypes[0] == OBJECT_TYPE && type.argumentTypes[1] == THROWABLE_TYPE
}

fun MethodNode.isSuspend() = isSuspend(name, desc)

fun MethodInsnNode.isSuspend() = isSuspend(name, desc)

internal fun continuationArgumentIndex(method: MethodNode) =
        if (!method.isSuspend()) null
        else Type.getType(method.desc).argumentTypes.size - continuationOffsetFromEndInDesc(method.name)


private fun continuationOffsetFromEndInDesc(name: String) = if (name.endsWith("\$default")) 3 else 1

private fun isSuspend(name: String, desc: String): Boolean {
    val type = Type.getType(desc)
    val offset = continuationOffsetFromEndInDesc(name)
    return type.argumentTypes.isNotEmpty()
            && (type.argumentTypes.lastIndexOf(CONTINUATION_TYPE) == type.argumentTypes.size - offset)
            && type.returnType == Type.getType(Any::class.java)
}

private fun prettyPrint(method: MethodNode, classNode: ClassNode, argumentValues: List<Any>? = null): String {
    fun argumentsString(argumentTypes: Array<Type>, argumentValues: List<Any>?) =
            argumentValues?.joinToString() ?: argumentTypes.joinToString(transform = { it.className.split('.').last() })

    val type = Type.getType(method.desc)
    val arguments = argumentsString(type.argumentTypes, argumentValues)
    val returnType = type.returnType.className.split('.').last()
    return "${classNode.name.replace('/', '.')}.${method.name}($arguments): $returnType"
}

internal fun buildMethodId(method: MethodNode, classNode: ClassNode): MethodIdWithInfo {
    val isSMForAnonymous = isStateMachineForAnonymousSuspendFunction(method) //FIXME how to determine is anonymous or not?
    val info = MethodInfo(isSMForAnonymous, method.isSuspend(), method.isDoResume(), isSMForAnonymous)
    return MethodIdWithInfo(method.name, classNode.name, method.desc, info, prettyPrint(method, classNode))
}

internal fun firstInstructionLineNumber(method: MethodNode) = //FIXME
        method.instructions.iterator().asSequence().filterIsInstance<LineNumberNode>().map { it.line }.min() ?: -1

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
        = instructions.toArray().filterIsInstance<LineNumberNode>().map { it.start to it.line }.toMap()

/*
* It is possible not to have line number before method call (e.g: inside synthetic bridge)
* */
internal fun methodCallLineNumber(instructions: InsnList): Map<MethodInsnNode, Int?> {
    val methodToLabel = methodCallNodeToLabelNode(instructions)
    val labelToLineNumber = labelNodeToLineNumber(instructions)
    return methodToLabel.map { it.key to labelToLineNumber[it.value] }.toMap()
}
