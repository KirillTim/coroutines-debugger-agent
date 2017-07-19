package mylibrary

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.coroutines.experimental.Continuation

/**
 * @author Kirill Timofeev
 */

data class MethodNodeClassNode(val method: MethodNode, val classNode: ClassNode)
data class MethodNameOwnerDesc(val name: String, val owner: String, val desc: String)

val doResumeToSuspendFunction = mutableMapOf<MethodNodeClassNode, UserDefinedSuspendFunction>()
private val unAssignedDoResumes = mutableMapOf<MethodNameOwnerDesc, MethodNodeClassNode>()
private val unAssignedSuspendFunctions = mutableSetOf<NamedSuspendFunction>()

fun updateDoResumeToSuspendFunctionMap(method: MethodNode, classNode: ClassNode) {
    if (isStateMachineForAnonymousSuspendFunction(method)) { //is doResume for itself
        val anonymous = AnonymousSuspendFunction(method, classNode, firstInstructionLineNumber(method))
        doResumeToSuspendFunction += (MethodNodeClassNode(method, classNode) to anonymous)
    } else if (method.isSuspend() && method.name != "invoke" && method.name != "create") {
        val named = NamedSuspendFunction(method, classNode, firstInstructionLineNumber(method))
        val key = MethodNameOwnerDesc(named.method.name, named.owner.name, named.method.desc)
        val doResume = unAssignedDoResumes.remove(key)
        if (doResume == null) {
            unAssignedSuspendFunctions += named
        } else {
            doResumeToSuspendFunction += (doResume to named)
        }
    } else if (method.isDoResume()) {
        val mnod = correspondingSuspendFunctionForDoResume(method)
        unAssignedSuspendFunctions.find {
            it.method.name == mnod.name && it.owner.name == mnod.owner && it.method.desc == mnod.desc
        }?.let {
            doResumeToSuspendFunction += (MethodNodeClassNode(method, classNode) to it)
            unAssignedSuspendFunctions.remove(it)
        }
    }
}

private fun firstInstructionLineNumber(method: MethodNode) = //FIXME
        method.instructions.iterator().asSequence().filterIsInstance<LineNumberNode>().map { it.line }.min() ?: -1

fun isStateMachineForAnonymousSuspendFunction(method: MethodNode): Boolean { //FIXME
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

fun correspondingSuspendFunctionForDoResume(method: MethodNode): MethodNameOwnerDesc { //FIXME
    if (!method.isDoResume()) {
        throw IllegalArgumentException("${method.name} should be doResume function")
    }
    val aReturn = method.instructions.last.previous
    val suspendFunctionCall = aReturn.previous
    if (suspendFunctionCall is MethodInsnNode && suspendFunctionCall.opcode == Opcodes.INVOKESTATIC
            && aReturn is InsnNode && aReturn.opcode == Opcodes.ARETURN) {
        return MethodNameOwnerDesc(suspendFunctionCall.name, suspendFunctionCall.owner, suspendFunctionCall.desc)
    }
    throw IllegalArgumentException("Unexpected end instructions in ${method.name}")
}

private fun isGetCOROUTINE_SUSPENDED(inst: AbstractInsnNode) =
        inst is MethodInsnNode && inst.name == "getCOROUTINE_SUSPENDED"
                && inst.owner == "kotlin/coroutines/experimental/intrinsics/IntrinsicsKt"
                && inst.desc == "()Ljava/lang/Object;"

private val OBJECT_TYPE = Type.getType(Any::class.java)
private val THROWABLE_TYPE = Type.getType(Throwable::class.java)

fun MethodNode.isDoResume(): Boolean {
    if (name != "doResume") return false
    val type = Type.getType(desc)
    return type.returnType == OBJECT_TYPE && type.argumentTypes.size == 2 // FIXME
            && type.argumentTypes[0] == OBJECT_TYPE && type.argumentTypes[1] == THROWABLE_TYPE
}

fun MethodNode.isSuspend() = isSuspend(name, desc)

fun MethodInsnNode.isSuspend() = isSuspend(name, desc)

fun continuationVarIndex(method: MethodNode) =
        if (!method.isSuspend()) null
        else Type.getType(method.desc).argumentTypes.size - continuationOffsetFromEndInDesc(method.name)


private fun continuationOffsetFromEndInDesc(name: String) = if (name.endsWith("\$default")) 3 else 1

private fun isSuspend(name: String, desc: String): Boolean { //TODO: what about invoke ?
    val type = Type.getType(desc)
    val CONTINUATION_TYPE = Type.getType(Continuation::class.java)
    val offset = continuationOffsetFromEndInDesc(name)
    return type.argumentTypes.isNotEmpty()
            && (type.argumentTypes.lastIndexOf(CONTINUATION_TYPE) == type.argumentTypes.size - offset)
            && type.returnType == Type.getType(Any::class.java)
}

fun insertPrintln(text: String, instructions: InsnList, insertAfter: AbstractInsnNode? = null): InsnList {
    val list = InsnList()
    list.add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
    list.add(LdcInsnNode(text))
    list.add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false))
    if (insertAfter != null) {
        instructions.insert(insertAfter, list)
    } else {
        instructions.insert(list)
    }
    return instructions
}

fun prettyPrint(owner: String, name: String, desc: String): String {
    val type = Type.getType(desc)
    val arguments = type.argumentTypes.joinToString(transform = { it.className.split('.').last() })
    val returnType = type.returnType.className.split('.').last()
    return "${owner.replace('/', '.')}.$name($arguments): $returnType"
}