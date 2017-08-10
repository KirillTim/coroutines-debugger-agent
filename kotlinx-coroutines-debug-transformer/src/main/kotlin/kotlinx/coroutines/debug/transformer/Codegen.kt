package kotlinx.coroutines.debug.transformer

import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodNode

/**
 * @author Kirill Timofeev
 */

private val MANAGER_CLASS_NAME = "kotlinx/coroutines/debug/manager/InstrumentedCodeEventsHandler"
private val AFTER_SUSPEND_CALL = "handleAfterSuspendCall"
private val DO_RESUME_ENTER = "handleDoResumeEnter"
private val DO_RESUME_EXIT = "handleDoResumeExit"

private inline fun code(block: InstructionAdapter.() -> Unit): InsnList =
        MethodNode().apply { block(InstructionAdapter(this)) }.instructions

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleAfterSuspendCall] with continuation
 * and index of function call from [kotlinx.coroutines.debug.manager.suspendCalls] list
 */
fun generateAfterSuspendCall(continuationVarIndex: Int, functionCallIndex: Int) =
        code {
            dup()
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(functionCallIndex)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_SUSPEND_CALL,
                    "(${OBJECT_TYPE.descriptor}${CONTINUATION_TYPE.descriptor}I)V", false)
        }

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleDoResumeEnter] with continuation
 * and index of doResume function in [kotlinx.coroutines.debug.manager.doResumeToSuspendFunctions] list
 */
fun generateHandleDoResumeCallEnter(continuationVarIndex: Int, doResumeIndex: Int)
        = generateContinuationAndIndexCall(continuationVarIndex, doResumeIndex, DO_RESUME_ENTER)

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleDoResumeExit] with continuation
 * and index of doResume function in [kotlinx.coroutines.debug.manager.doResumeToSuspendFunctions] list
 */
fun generateHandleDoResumeCallExit(continuationVarIndex: Int, doResumeIndex: Int)
        = generateContinuationAndIndexCall(continuationVarIndex, doResumeIndex, DO_RESUME_EXIT)

private fun generateContinuationAndIndexCall(continuationVarIndex: Int, index: Int, methodToCall: String) =
        code {
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(index)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, methodToCall,
                    "(${CONTINUATION_TYPE.descriptor}I)V", false)
        }

fun insertPrintln(text: String, instructions: InsnList, insertAfter: AbstractInsnNode? = null) {
    code {
        getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
        aconst(text)
        visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(${STRING_TYPE.descriptor})V", false)
    }.apply {
        if (insertAfter != null)
            instructions.insert(insertAfter, this)
        else
            instructions.insert(this)
    }
}