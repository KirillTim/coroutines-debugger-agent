package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.MethodId
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*

/**
 * @author Kirill Timofeev
 */

private val CONTINUATION = "Lkotlin/coroutines/experimental/Continuation;"
private val STRING = "Ljava/lang/String;"
private val OBJECT = "Ljava/lang/Object;"

private val MANAGER_CLASS_NAME = "kotlinx/coroutines/debug/manager/Manager"
private val AFTER_SUSPEND_CALL = "afterSuspendCall"
private val HANDLE_DO_RESUME = "handleDoResume"

fun generateAfterSuspendCall(suspendCall: MethodInsnNode, continuationVarIndex: Int, calledFromFunction: String,
                             file: String, line: Int): InsnList {
    val list = InsnList()
    list.add(InsnNode(Opcodes.DUP))
    list.add(IntInsnNode(Opcodes.ALOAD, continuationVarIndex))
    list.add(LdcInsnNode(suspendCall.name))
    list.add(LdcInsnNode(suspendCall.desc)) //FIXME can find functionCall by this three parameters
    list.add(LdcInsnNode(suspendCall.owner))
    list.add(LdcInsnNode(calledFromFunction))
    list.add(LdcInsnNode("$file:$line")) //FIXME?
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_SUSPEND_CALL,
            "($OBJECT$CONTINUATION$STRING$STRING$STRING$STRING$STRING)V", false))
    return list
}

//each suspend functionCall can be determined by doResume ownerNode class
fun generateHandleDoResumeCall(continuationVarIndex: Int, forFunction: MethodId): InsnList {
    val list = InsnList()
    list.add(IntInsnNode(Opcodes.ALOAD, continuationVarIndex))
    list.add(LdcInsnNode(forFunction.name))
    list.add(LdcInsnNode(forFunction.owner))
    list.add(LdcInsnNode(forFunction.desc))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, HANDLE_DO_RESUME,
            "($CONTINUATION$STRING$STRING$STRING)V", false))
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