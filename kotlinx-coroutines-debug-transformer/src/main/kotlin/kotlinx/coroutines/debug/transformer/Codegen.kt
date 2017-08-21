package kotlinx.coroutines.debug.transformer

import jdk.internal.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.tree.*

/**
 * @author Kirill Timofeev
 */

private val COMPLETION_WRAPPER_CLASS_NAME = "kotlinx/coroutines/debug/manager/WrappedCompletion"
private val WRAP_COMPLETION = "maybeWrapCompletionAndCreateNewCoroutine"
private val MANAGER_CLASS_NAME = "kotlinx/coroutines/debug/manager/InstrumentedCodeEventsHandler"
private val AFTER_NAMED_SUSPEND_CALL = "handleAfterNamedSuspendCall"
private val AFTER_INVOKE_SUSPEND_CALL = "handleAfterInvokeSuspendCall"
private val DO_RESUME_ENTER = "handleDoResumeEnter"

internal inline fun code(block: InstructionAdapter.() -> Unit): InsnList =
        MethodNode().apply { block(InstructionAdapter(this)) }.instructions

fun generateNewWrappedCompletion(completionIndex: Int) =
        code {
            load(completionIndex, CONTINUATION_TYPE)
            visitMethodInsn(Opcodes.INVOKESTATIC, COMPLETION_WRAPPER_CLASS_NAME, WRAP_COMPLETION,
                    "(${CONTINUATION_TYPE.descriptor})${CONTINUATION_TYPE.descriptor}", false)
            store(completionIndex, CONTINUATION_TYPE)
        }

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleAfterNamedSuspendCall]
 * with continuation and index of function call from [kotlinx.coroutines.debug.manager.allSuspendCalls] list
 */
fun generateAfterNamedSuspendCall(continuationVarIndex: Int, functionCallIndex: Int) =
        code {
            dup()
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(functionCallIndex)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_NAMED_SUSPEND_CALL,
                    "(${OBJECT_TYPE.descriptor}${CONTINUATION_TYPE.descriptor}I)V", false)
        }

private fun generateAfterInvokeSuspendCall(lambdaVarIndex: Int, continuationVarIndex: Int, functionCallIndex: Int) =
        code {
            dup()
            load(continuationVarIndex, CONTINUATION_TYPE)
            load(lambdaVarIndex, OBJECT_TYPE)
            aconst(functionCallIndex)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, AFTER_INVOKE_SUSPEND_CALL,
                    "(${OBJECT_TYPE.descriptor}${CONTINUATION_TYPE.descriptor}${OBJECT_TYPE.descriptor}I)V", false)
        }

private fun generateSaveStackToLocalVars(firstIndex: Int, stackSize: Int) =
        code {
            (0 until stackSize).forEach {
                store(firstIndex + it, OBJECT_TYPE)
            }
        }

private fun generateRestoreStackFromLocalVars(firstIndex: Int, stackSize: Int) =
        code {
            (0 until stackSize).reversed().forEach {
                load(firstIndex + it, OBJECT_TYPE)
            }
        }

private fun generateStoreLambdaObjectFromStack(firstIndex: Int, stackSize: Int): Pair<Int, InsnList> {
    val instructions = generateSaveStackToLocalVars(firstIndex, stackSize)
    val lambdaObjectVarIndex = firstIndex + stackSize
    instructions.add(code {
        dup()
        store(lambdaObjectVarIndex, OBJECT_TYPE)
    })
    instructions.add(generateRestoreStackFromLocalVars(firstIndex, stackSize))
    return Pair(lambdaObjectVarIndex, instructions)
}

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleAfterInvokeSuspendCall]
 * with continuation, lambda object and index of function call from [kotlinx.coroutines.debug.manager.allSuspendCalls] list
 */
fun generateInvokeSuspendCallHandler(currentMethod: MethodNode,
                                     invokeInsn: MethodInsnNode,
                                     continuationVarIndex: Int, functionCallIndex: Int): Pair<InsnList, InsnList> {
    val firstIndex = currentMethod.instructions.sequence
            .filter { it.isASTORE() }.filterIsInstance<VarInsnNode>().map { it.`var` }.max()!! + 1
    val stackSize = Type.getType(invokeInsn.desc).argumentTypes.size
    val (lambdaObjectVarIndex, before) = generateStoreLambdaObjectFromStack(firstIndex, stackSize)
    val after = generateAfterInvokeSuspendCall(lambdaObjectVarIndex, continuationVarIndex, functionCallIndex)
    return Pair(before, after)
}

/**
 * Generate call of [kotlinx.coroutines.debug.manager.InstrumentedCodeEventsHandler.handleDoResumeEnter] with continuation
 * and index of doResume function in [kotlinx.coroutines.debug.manager.allDoResumeCalls] list
 */
fun generateHandleDoResumeCallEnter(continuationVarIndex: Int, doResumeIndex: Int) =
        code {
            load(0, COROUTINE_IMPL_TYPE)
            getfield(COROUTINE_IMPL_TYPE.descriptor, "completion", CONTINUATION_TYPE.descriptor)
            load(continuationVarIndex, CONTINUATION_TYPE)
            aconst(doResumeIndex)
            visitMethodInsn(Opcodes.INVOKESTATIC, MANAGER_CLASS_NAME, DO_RESUME_ENTER,
                    "(${CONTINUATION_TYPE.descriptor}${CONTINUATION_TYPE.descriptor}I)V", false)
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