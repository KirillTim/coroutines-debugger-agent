package agent

import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.coroutines.experimental.Continuation

/**
 * @author Kirill Timofeev
 */

fun isStateMachineForAnonymousSuspendFunction(method: MethodNode): Boolean { //FIXME
    if (method.name != "doResume") return false
    val type = Type.getType(method.desc)
    val OBJECT_TYPE = Type.getType(Any::class.java)
    if (type.returnType != OBJECT_TYPE || type.argumentTypes.size != 2
            || type.argumentTypes[0] != OBJECT_TYPE || type.argumentTypes[1] != Type.getType(Throwable::class.java)) {
        return false
    }
    val getSuspendedConst = method.instructions.get(0)
    val l0 = method.instructions.get(1)
    val getLabel = method.instructions.get(5)
    val tableSwitch = method.instructions.get(6)
    return isGetCOROUTINE_SUSPENDED(getSuspendedConst) && l0 is LabelNode
            && getLabel is FieldInsnNode && getLabel.name == "label" && tableSwitch is TableSwitchInsnNode
}

fun methodCallNodeToLabelNode(instructions: InsnList): Map<MethodInsnNode, LabelNode> {
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

private fun isGetCOROUTINE_SUSPENDED(inst: AbstractInsnNode) =
        inst is MethodInsnNode && inst.name == "getCOROUTINE_SUSPENDED"
                && inst.owner == "kotlin/coroutines/experimental/intrinsics/IntrinsicsKt"
                && inst.desc == "()Ljava/lang/Object;"

fun MethodNode.isSuspend() = isSuspend(name, desc)

fun MethodInsnNode.isSuspend() = isSuspend(name, desc)

fun continuationVarIndex(method: MethodNode) =
        if (!method.isSuspend()) null
        else Type.getType(method.desc).argumentTypes.size - continuationOffsetFromEndInDesc(method.name)


private fun continuationOffsetFromEndInDesc(name: String) = if (name.endsWith("\$default")) 3 else 1

private fun isSuspend(name: String, desc: String): Boolean {
    val type = Type.getType(desc)
    val CONTINUATION_TYPE = Type.getType(Continuation::class.java)
    val offset = continuationOffsetFromEndInDesc(name)
    return type.argumentTypes.isNotEmpty()
            && (type.argumentTypes.lastIndexOf(CONTINUATION_TYPE) == type.argumentTypes.size - offset)
            && type.returnType == Type.getType(Any::class.java)
}