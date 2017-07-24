package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.MethodId
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.InstructionAdapter
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

private inline fun code(block: InstructionAdapter.() -> Unit) =
    MethodNode().apply { block(InstructionAdapter(this)) }.instructions

fun generateAfterSuspendCall(
    suspendCall: MethodInsnNode, continuationVarIndex: Int, calledFromFunction: String,
    file: String, line: Int
) =
    code {
        dup()
        load(continuationVarIndex, Type.getType(CONTINUATION))
        aconst(suspendCall.name)
        aconst(suspendCall.desc)
        aconst(suspendCall.owner)
        aconst(calledFromFunction)// todo: remove it
        aconst("$file:$line") // todo: renumber this tuple
        visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_SUSPEND_CALL,
            "($OBJECT$CONTINUATION$STRING$STRING$STRING$STRING$STRING)V", false)
    }

//each suspend functionCall can be determined by doResume ownerNode class
fun generateHandleDoResumeCall(continuationVarIndex: Int, forFunction: MethodId) =
    InsnList().apply { // todo: use code
        add(IntInsnNode(Opcodes.ALOAD, continuationVarIndex))
        add(LdcInsnNode(forFunction.name))
        add(LdcInsnNode(forFunction.owner))
        add(LdcInsnNode(forFunction.desc))
        add(MethodInsnNode(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, HANDLE_DO_RESUME,
                "($CONTINUATION$STRING$STRING$STRING)V", false))
    }
    
fun insertPrintln(text: String, instructions: InsnList, insertAfter: AbstractInsnNode? = null) =
    InsnList().apply { // todo: use code
        add(FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"))
        add(LdcInsnNode(text))
        add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false))
        if (insertAfter != null) {
            instructions.insert(insertAfter, this)
        } else {
            instructions.insert(this)
        }
    }