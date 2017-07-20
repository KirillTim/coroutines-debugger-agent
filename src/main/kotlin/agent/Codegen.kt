package agent

import mylibrary.MethodNameOwnerDesc
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

/**
 * @author Kirill Timofeev
 */

fun generateAfterSuspendCall(suspendCall: MethodInsnNode, continuationVarIndex: Int, calledFrom: String): InsnList {
    val list = InsnList()
    list.add(InsnNode(Opcodes.DUP))
    list.add(IntInsnNode(Opcodes.ALOAD, continuationVarIndex))
    list.add(LdcInsnNode(suspendCall.name))
    list.add(LdcInsnNode(suspendCall.desc)) //FIXME can find function by this three parameters
    list.add(LdcInsnNode(suspendCall.owner))
    list.add(LdcInsnNode(calledFrom))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutinesManager", "afterSuspendCall",
            "(Ljava/lang/Object;Lkotlin/coroutines/experimental/Continuation;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false))
    return list
}

//each suspend function can be determined by doResume owner class
fun generateHandleDoResumeCall(continuationVarIndex: Int, forFunction: MethodNameOwnerDesc): InsnList {
    val list = InsnList()
    list.add(IntInsnNode(Opcodes.ALOAD, continuationVarIndex))
    list.add(LdcInsnNode(forFunction.name))
    list.add(LdcInsnNode(forFunction.owner))
    list.add(LdcInsnNode(forFunction.desc))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutinesManager", "handleDoResume",
            "(Lkotlin/coroutines/experimental/Continuation;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false))
    return list
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
