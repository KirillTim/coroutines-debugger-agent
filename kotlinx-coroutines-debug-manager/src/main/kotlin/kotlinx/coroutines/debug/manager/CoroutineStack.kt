package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author Kirill Timofeev
 */

private data class CoroutineStackFrame(val continuation: Continuation<*>, val functionCall: FunctionCall) {
    fun prettyPrint() = "continuation hash: ${continuation.hashCode()}, $functionCall"
}

interface CoroutineStack {
    val context: CoroutineContext

    fun getSuspendedStack(): List<FunctionCall>

    fun isEntryPoint(method: MethodId): Boolean //FIXME better signature?

    /**
     * @return true if new frames were add to stack, false otherwise
     */
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall): Boolean

    fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend)
}

class CoroutineStackImpl(override val context: CoroutineContext) : CoroutineStack {

    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<FunctionCall> = stack.map { it.functionCall }

    override fun isEntryPoint(method: MethodId) = entryPoint?.functionCall?.function == method

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall): Boolean {
        if (functionCall.function.name.endsWith("\$default")) {
            val delegatedCall = temporaryStack.lastOrNull()
            requireNotNull(delegatedCall != null, { "can't find delegated call for  $functionCall" })
            temporaryStack.removeAt(temporaryStack.lastIndex)
            temporaryStack += delegatedCall!!.copy(functionCall = delegatedCall.functionCall.copy(position = functionCall.position))
        }
        temporaryStack.add(CoroutineStackFrame(continuation, functionCall))
        if (continuation === topCurrentContinuation
                && functionCall.fromFunction == stack.firstOrNull()?.functionCall?.function) {
            applyStack()
            return true
        }
        return false
    }

    private fun applyStack() {
        Logger.default.debug {
            buildString {
                append("stack applied\n")
                append("topCurrentContinuation hash: ${topCurrentContinuation?.hashCode()}\n")
                val (cont, call) = entryPoint!!
                append("entry point: hash: ${cont.hashCode()}, func: ${call}\n")
                append("temp: ${temporaryStack.joinToString("\n", transform = { it.prettyPrint() })}\n")
                append("stack: ${stack.joinToString("\n", transform = { it.prettyPrint() })}")
            }
        }
        stack.addAll(0, temporaryStack)
        temporaryStack.clear()
        topCurrentContinuation = null
    }

    override fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend) {
        Logger.default.debug { "handleDoResumeEnter for ${function.doResume}, ${function.suspend.method}" }
        if (entryPoint == null) {
            entryPoint = CoroutineStackFrame(continuation,
                    FunctionCall(function.doResume.method, function.doResumeCallPosition ?: CallPosition.UNKNOWN))
            stack.add(entryPoint!!)
            topCurrentContinuation = continuation
            Logger.default.debug { "entry point: ${entryPoint?.prettyPrint()}" }
            return
        }
        val currentTopFrame = if (function.doResume.method != function.suspend.method)
            stack.indexOfLast { it.continuation === continuation } + 1
        else  //doResume for itself, clean everything with our continuation up to doResume call
            stack.indexOfLast {
                it.continuation === continuation
                        && it.functionCall.function == function.doResume.method
            }

        if (currentTopFrame > 0) {
            topCurrentContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
    }

    fun prettyPrint() = "$context:\n" + //FIXME
            if (stack.isEmpty()) "<empty>"
            else stack.joinToString("\n") { it.prettyPrint() } + "\n--------------"

}