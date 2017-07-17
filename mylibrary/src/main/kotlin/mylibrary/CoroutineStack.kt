package mylibrary

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author Kirill Timofeev
 */

sealed class CoroutineStackFrame(open val continuation: Continuation<*>) {
    fun prettyPrint() = "continuation hash: ${continuation.hashCode()}, " + when (this) {
        is UserDefined -> this.function.prettyPrint(arguments)
        is LibraryFunction -> this.name
    }
}

data class UserDefined(override val continuation: Continuation<*>, val function: UserDefinedSuspendFunction,
                       val arguments: List<Any>? = null) : CoroutineStackFrame(continuation)

data class LibraryFunction(override val continuation: Continuation<*>, val name: String)
    : CoroutineStackFrame(continuation)

class CoroutineStack(val context: CoroutineContext) {
    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: UserDefinedSuspendFunction? = null
    private var topContinuation: Continuation<*>? = null

    fun handleSuspended(continuation: Continuation<*>, function: UserDefinedSuspendFunction)
            = push(UserDefined(continuation, function))

    fun handleSuspended(continuation: Continuation<*>, libraryFunction: String)
            = push(LibraryFunction(continuation, libraryFunction))

    private fun push(stackFrame: CoroutineStackFrame) {
        if (topContinuation != null) {
            if (topContinuation === stackFrame.continuation && stackFrame is UserDefined) {
                System.err.println("add reversed temp to stack")
                stack.add(0, stackFrame)
                stack.addAll(0, temporaryStack)
                temporaryStack.clear()
                topContinuation = null
            } else {
                System.err.println("add ${stackFrame.prettyPrint()} to temp")
                temporaryStack.add(stackFrame)
            }
        } else {
            stack.add(stackFrame)
        }
    }

    fun handleDoResume(continuation: Continuation<*>, function: UserDefinedSuspendFunction) {
        if (entryPoint == null) {
            entryPoint = function
        }
        val currentTopFrame = stack.indexOfFirst { it.continuation === continuation } + 1 //FIXME
        if (currentTopFrame > 0) {
            topContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
    }

    fun prettyPrint() = "$context:\n" +
            if (stack.isEmpty()) "<empty>"
            else stack.first().prettyPrint() + " <- suspended here\n" +
                    stack.drop(1).joinToString("\n") { it.prettyPrint() } +
                    "\n--------------"
}