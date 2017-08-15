package kotlinx.coroutines.debug.manager

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

data class CoroutineStackFrame(
        val continuation: Continuation<*>,
        val call: MethodCall) {
    override fun toString() = "continuation: ${continuation.hashCode()}, $call"
}

sealed class WrappedContext(open val context: CoroutineContext) {
    abstract val name: String
    override fun toString() = name
}

private fun CoroutineContext.isSingleton() = this is EmptyCoroutineContext

private fun CoroutineContext.wrap() = if (isSingleton()) SingletonContext(this) else NormalContext(this)

private fun CoroutineContext.getPrettyName(): String {
    val parts = "$this".drop(1).dropLast(1).split(", ").map {
        val firstParenthesis = it.indexOf('(')
        if (firstParenthesis == -1) it to ""
        else it.take(firstParenthesis) to it.drop(firstParenthesis + 1).dropLast(1)
    }.toMap()
    val name = parts["CoroutineName"] ?: "coroutine"
    val id = parts["CoroutineId"]
    return "$name #$id"
}

data class NormalContext(override val context: CoroutineContext) : WrappedContext(context) {
    override val name = context.getPrettyName()
}

data class SingletonContext private constructor(override val context: CoroutineContext, private val id: Int)
    : WrappedContext(context) {
    constructor(context: CoroutineContext) : this(context, nextId.getAndIncrement())

    override val name = "$context #$id"

    companion object {
        private val nextId = AtomicInteger(0)
    }
}

sealed class CoroutineStatus(private val status: String) {
    override fun toString() = status

    object Created : CoroutineStatus("Created")
    object Running : CoroutineStatus("Running")
    object Suspended : CoroutineStatus("Suspended")
}

class CoroutineStack(val initialCompletion: WrappedCompletion) {
    val context: WrappedContext = initialCompletion.context.wrap()
    val name: String = context.name
    var thread: Thread = Thread.currentThread()
        private set
    var status: CoroutineStatus = CoroutineStatus.Created
        private set
    var topContinuation: Continuation<*> = initialCompletion
        private set
    private val stack = mutableListOf<CoroutineStackFrame>() //FIXME: deque
    private val unAppliedStack = mutableListOf<CoroutineStackFrame>()

    fun getSnapshot(): Snapshot = synchronized(this) { Snapshot(name, context, status, thread, stack.map { it.call }) }

    /**
     * @return true if new frames were add to stack, false otherwise
     */
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, call: MethodCall): Boolean {
        if (call.method.name.endsWith("\$default")) {
            val delegatedCall = requireNotNull(unAppliedStack.lastOrNull(), { "can't find delegated call for  $call" })
            unAppliedStack[unAppliedStack.lastIndex] =
                    delegatedCall.copy(call = delegatedCall.call.copy(position = call.position))
        }
        unAppliedStack.add(CoroutineStackFrame(continuation, call))
        if (continuation === topContinuation && call.fromMethod == stack.first().call.method) {
            applyStack()
            return true
        }
        return false
    }

    private fun applyStack() {
        debug {
            buildString {
                append("stack applied\n")
                append("topCurrentContinuation hash: ${topContinuation.hashCode()}\n")
                append("initialCompletion: ${initialCompletion.hashCode()}\n")
                append("temp: ${unAppliedStack.joinToString("\n")}\n")
                append("stack: ${stack.joinToString("\n")}")
            }
        }
        status = CoroutineStatus.Suspended
        stack.addAll(0, unAppliedStack)
        topContinuation = stack.first().continuation
        unAppliedStack.clear()
    }

    fun handleDoResume(
            completion: Continuation<*>,
            continuation: Continuation<*>, function: DoResumeForSuspend) {
        thread = Thread.currentThread()
        status = CoroutineStatus.Running
        val call = MethodCall(function.doResume.method, function.doResumeCallPosition ?: CallPosition.UNKNOWN)
        if (stack.isEmpty() || stack.first().continuation == completion) {
            topContinuation = continuation
            stack.add(0, CoroutineStackFrame(continuation, call))
        }
        val currentTopFrame = if (function.doResumeForItself)
            stack.indexOfLast { it.continuation === continuation && it.call.method == function.doResume.method }
        else stack.indexOfLast { it.continuation === continuation } + 1
        if (currentTopFrame > 0) {
            topContinuation = continuation
            stack.dropInplace(currentTopFrame)
        }
    }
}

/**
 * Remove first [n] elements inplace
 */
fun MutableList<*>.dropInplace(n: Int) {
    val resultSize = size - n
    if (resultSize <= 0) clear()
    else
        while (size > resultSize)
            removeAt(0)
}