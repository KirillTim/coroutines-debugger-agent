package mylibrary

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author Kirill Timofeev
 */

private data class CoroutineStackFrame(val continuation: Continuation<*>, val function: SuspendFunction) {
    fun prettyPrint() = "continuation hash: ${continuation.hashCode()}, ${function.prettyPrint()}"
}

interface CoroutineStack {
    val context: CoroutineContext
    fun getSuspendedStack(): List<SuspendFunction>
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, function: SuspendFunction)
    fun handleDoResume(continuation: Continuation<*>, function: UserDefinedSuspendFunction)
}

open class CoroutineStackImpl(override val context: CoroutineContext, val onCoroutineSuspend: CoroutineStackImpl.() -> Unit) : CoroutineStack {

    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<SuspendFunction> = stack.map { it.function }

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, function: SuspendFunction) {
        val frame = CoroutineStackFrame(continuation, function)
        temporaryStack.add(frame)
        if (topCurrentContinuation != null) { //already have something on stack
            if (topCurrentContinuation === continuation && function is UserDefinedSuspendFunction) {
                applyStack()
            }
        } else if (stack.isEmpty()) {
            if (entryPoint?.continuation === frame.continuation) {
                temporaryStack.add(entryPoint!!)
                applyStack()
            }
        }
    }

    // TODO: can't handle tail recursion for suspend functions.
    // map from continuation to first suspend function call needed to restore different calls with same continuation
    private fun applyStack() {
        System.err.println("stack applied")
        System.err.println("topCurrentContinuation hash: ${topCurrentContinuation?.hashCode()}")
        System.err.println("entry point: hash: ${entryPoint?.continuation?.hashCode()}, func: ${entryPoint?.function}")
        try {
            System.err.println("temp: ${temporaryStack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print temporaryStack")
        }
        try {
            System.err.println("stack: ${stack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print stack")
        }
        stack.addAll(0, temporaryStack)
        temporaryStack.clear()
        topCurrentContinuation = null
        onCoroutineSuspend()
    }

    override fun handleDoResume(continuation: Continuation<*>, function: UserDefinedSuspendFunction) {
        if (entryPoint == null) {
            entryPoint = CoroutineStackFrame(continuation, function)
            System.err.println("entry point: ${entryPoint?.prettyPrint()}")
        }
        val currentTopFrame = stack.indexOfFirst { it.continuation === continuation } + 1 //FIXME
        if (currentTopFrame > 0) {
            topCurrentContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
    }

    fun prettyPrint() = "$context:\n" + //FIXME
            if (stack.isEmpty()) "<empty>"
            else stack.first().prettyPrint() + " <- suspended here\n" +
                    stack.drop(1).joinToString("\n") { it.prettyPrint() } +
                    "\n--------------"
}