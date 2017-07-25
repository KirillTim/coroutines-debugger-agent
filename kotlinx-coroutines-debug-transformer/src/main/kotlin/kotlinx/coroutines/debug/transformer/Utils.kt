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

private fun AbstractInsnNode?.isGetCOROUTINE_SUSPENDED() =
        this is MethodInsnNode && name == "getCOROUTINE_SUSPENDED"
                && owner == "kotlin/coroutines/experimental/intrinsics/IntrinsicsKt"
                && desc == "()Ljava/lang/Object;"

private fun AbstractInsnNode?.isLabel() =
        this is FieldInsnNode && name == "label"

internal fun MethodNode.isStateMachineForAnonymousSuspendFunction(): Boolean =
    isDoResume() &&
        instructions[0].isGetCOROUTINE_SUSPENDED() &&
        instructions[1] is LabelNode &&
        instructions[5].isLabel() &&
        instructions[6] is TableSwitchInsnNode

internal fun MethodNode.correspondingSuspendFunctionForDoResume(): SuspendFunction { //FIXME
    require(isDoResume()) { "${name} should be doResume functionCall" }
    val aReturn = instructions.last.previous // todo: too fragile
    require(aReturn is InsnNode && aReturn.opcode == Opcodes.ARETURN) { "Must be areturn" }
    val suspendFunctionCall = aReturn.previous as MethodInsnNode
    require (suspendFunctionCall.opcode == Opcodes.INVOKESTATIC)
    return NamedSuspendFunction(MethodId(suspendFunctionCall.name, suspendFunctionCall.owner, suspendFunctionCall.desc))
}

private fun Type.isResumeMethodDesc() =
    (returnType == OBJECT_TYPE && argumentTypes.size == 2 // FIXME
        && argumentTypes[0] == OBJECT_TYPE && argumentTypes[1] == THROWABLE_TYPE)

fun MethodNode.isDoResume(): Boolean = name == "doResume" && Type.getType(desc).isResumeMethodDesc()


fun MethodNode.isSuspend() = isSuspend(name, desc)

fun MethodInsnNode.isSuspend() = isSuspend(name, desc)

internal fun continuationArgumentIndex(method: MethodNode) =
        if (!method.isSuspend()) null
        else Type.getType(method.desc).argumentTypes.size - continuationOffsetFromEndInDesc(method.name)

private fun continuationOffsetFromEndInDesc(name: String) = if (name.endsWith("\$default")) 3 else 1

private fun isSuspend(name: String, desc: String): Boolean {
    val offset = continuationOffsetFromEndInDesc(name)
    val type = Type.getType(desc)
    return type.argumentTypes.getOrNull(type.argumentTypes.size - offset) == CONTINUATION_TYPE
            && type.returnType == Type.getType(Any::class.java)
}

private fun prettyPrint(method: MethodNode, classNode: ClassNode, argumentValues: List<Any>? = null): String {
    val type = Type.getType(method.desc)
    val arguments = argumentValues?.joinToString() ?:
        type.argumentTypes.joinToString(transform = { it.className.split('.').last() })
    val returnType = type.returnType.className.split('.').last()
    return "${classNode.name.replace('/', '.')}.${method.name}($arguments): $returnType"
}

internal fun buildMethodId(method: MethodNode, classNode: ClassNode): MethodId {
    val isSMForAnonymous = method.isStateMachineForAnonymousSuspendFunction() //FIXME how to determine is anonymous or not?
    val info = MethodInfo(isSMForAnonymous, method.isSuspend(), method.isDoResume(), isSMForAnonymous)
    return MethodId(method.name, classNode.name, method.desc, info, prettyPrint(method, classNode))
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
