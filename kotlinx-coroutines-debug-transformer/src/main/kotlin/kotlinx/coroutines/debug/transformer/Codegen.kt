package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.doResumeToSuspendFunctions
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * @author Kirill Timofeev
 */

private val STRING = STRING_TYPE.descriptor
private val OBJECT = OBJECT_TYPE.descriptor

private val MANAGER_CLASS_NAME = "kotlinx/coroutines/debug/manager/Manager"
private val AFTER_SUSPEND_CALL = "afterSuspendCall"
private val HANDLE_DO_RESUME = "handleDoResume"

private inline fun code(block: InstructionAdapter.() -> Unit): InsnList =
        MethodNode().apply { block(InstructionAdapter(this)) }.instructions

fun generateAfterSuspendCall(
        suspendCall: MethodInsnNode, continuationVarIndex: Int, calledFromFunction: String,
        file: String, line: Int
) =
        code {
            dup()
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(suspendCall.name)
            aconst(suspendCall.desc)
            aconst(suspendCall.owner)
            aconst(calledFromFunction)// todo: remove it
            aconst("$file:$line") // todo: renumber this tuple
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_SUSPEND_CALL,
                    "($OBJECT${CONTINUATION_TYPE.descriptor}$STRING$STRING$STRING$STRING$STRING)V", false)
        }

/**
 * Generate call of [kotlinx.coroutines.debug.manager.Manager.handleDoResume] with continuation
 * and index of doResume function in [doResumeToSuspendFunctions] list
 */
fun generateHandleDoResumeCall(continuationVarIndex: Int, doResumeIndex: Int) =
        code {
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(doResumeIndex)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, HANDLE_DO_RESUME,
                    "(${CONTINUATION_TYPE.descriptor}I)V", false)
        }


fun insertPrintln(text: String, instructions: InsnList, insertAfter: AbstractInsnNode? = null) {
    code {
        getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
        aconst(text)
        visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "($STRING)V", false)
    }.apply {
        if (insertAfter != null)
            instructions.insert(insertAfter, this)
        else
            instructions.insert(this)
    }
}