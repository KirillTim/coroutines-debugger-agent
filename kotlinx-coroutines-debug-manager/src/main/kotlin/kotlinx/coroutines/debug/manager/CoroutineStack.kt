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
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall)
    fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend)
}

class CoroutineStackImpl(override val context: CoroutineContext,
                         val onCoroutineSuspend: CoroutineStackImpl.() -> Unit) : CoroutineStack {

    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<FunctionCall> = stack.map { it.functionCall }

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall) {
        temporaryStack.add(CoroutineStackFrame(continuation, functionCall))
        if (continuation === topCurrentContinuation
                && functionCall.fromFunction == stack.firstOrNull()?.functionCall?.function) {
            applyStack()
        }
    }

    private fun applyStack() {
        /*System.err.println("stack applied")
        System.err.println("topCurrentContinuation hash: ${topCurrentContinuation?.hashCode()}")
        System.err.println("entry point: hash: ${entryPoint?.continuation?.hashCode()}, func: ${entryPoint?.functionCall}")
        try {
            System.err.println("temp: ${temporaryStack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  fullInfo temporaryStack")
        }
        try {
            System.err.println("stack: ${stack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  fullInfo stack")
        }*/
        stack.addAll(0, temporaryStack)
        temporaryStack.clear()
        topCurrentContinuation = null
        onCoroutineSuspend()
    }

    override fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend) {
        System.err.println("handleDoResume for ${function.doResume}, ${function.suspend.method}")
        if (entryPoint == null) {
            entryPoint = CoroutineStackFrame(continuation,
                    FunctionCall(function.doResume.method, function.doResumeCallPosition ?: CallPosition.UNKNOWN))
            stack.add(entryPoint!!)
            topCurrentContinuation = continuation
            System.err.println("entry point: ${entryPoint?.prettyPrint()}")
            return
        }
        val currentTopFrame = if (function.doResume.method != function.suspend.method) {
            System.err.println("not doResume for self")
            stack.indexOfLast { it.continuation === continuation } + 1
        } else {  //doResume for itself, clean everything with our continuation up to doResume call
            System.err.println("doResume for self")
            stack.indexOfLast { it.continuation == continuation && it.functionCall.function == function.doResume.method }
        }
        System.err.println("currentTopFrame: $currentTopFrame")
        if (currentTopFrame > 0) {
            topCurrentContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
        System.err.println("stack size after handleDoResume: ${stack.size}")
    }

    fun prettyPrint() = "$context:\n" + //FIXME
            if (stack.isEmpty()) "<empty>"
            else stack.first().prettyPrint() + " <- suspended here\n" +
                    stack.drop(1).joinToString("\n") { it.prettyPrint() } +
                    "\n--------------"

}