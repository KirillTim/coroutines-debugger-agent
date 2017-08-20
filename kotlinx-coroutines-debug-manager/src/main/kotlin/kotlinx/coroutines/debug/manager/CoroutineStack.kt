package kotlinx.coroutines.debug.manager

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

private sealed class FrameId(open val value: Continuation<*>)
private data class ContinuationId(override val value: Continuation<*>) : FrameId(value) {
    override fun toString() = "Continuation(${value.hashCode()})"
}

private data class CompletionId(override val value: Continuation<*>) : FrameId(value) {
    override fun toString() = "Completion(${value.hashCode()})"
}

private data class CoroutineStackFrame(val id: FrameId, val call: MethodCall) {
    override fun toString() = "$id, $call"
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
    var topFrameCompletion: Continuation<*> = initialCompletion //key to find stack for doResume
        private set
    private var topContinuation: Continuation<*> = initialCompletion
    private val stack = mutableListOf<CoroutineStackFrame>() //FIXME: deque
    private val unAppliedStack = mutableListOf<CoroutineStackFrame>()

    fun getSnapshot(): Snapshot = synchronized(this) { Snapshot(name, context, status, thread, stack.map { it.call }) }

    /**
     * @return true if new frames were add to stack, false otherwise
     */
    fun handleSuspendFunctionReturn(completion: Continuation<*>, call: MethodCall): Boolean {
        if (call.method.name.endsWith("\$default")) {
            val delegatedCall = requireNotNull(unAppliedStack.lastOrNull(), { "can't find delegated call for  $call" })
            unAppliedStack[unAppliedStack.lastIndex] =
                    delegatedCall.copy(call = delegatedCall.call.copy(position = call.position))
        }
        unAppliedStack.add(CoroutineStackFrame(CompletionId(completion), call))
        if (completion === topContinuation && (call.fromMethod == stack.first().call.method
                || call.fromMethod?.name == "doResume" && stack.first().call.method.name == "invoke")) {
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
                append("temp:\n${unAppliedStack.joinToString("\n")}\n")
                append("stack:\n${stack.joinToString("\n")}")
            }
        }
        status = CoroutineStatus.Suspended
        stack.addAll(0, unAppliedStack)
        topFrameCompletion = stack.first().id.value
        unAppliedStack.clear()
    }

    fun handleDoResume(completion: Continuation<*>,
                       continuation: Continuation<*>, function: DoResumeForSuspend): Continuation<*> {
        thread = Thread.currentThread()
        status = CoroutineStatus.Running
        if (stack.isEmpty()) {
            require(initialCompletion === completion)
            topContinuation = continuation
            val call = MethodCall(function.doResume.method, function.doResumeCallPosition ?: CallPosition.UNKNOWN)
            stack.add(0, CoroutineStackFrame(ContinuationId(continuation), call))
            return continuation
        }
        topContinuation = continuation
        topFrameCompletion = completion
        val framesToRemove = stack.indexOfLast { it.id is CompletionId && it.id.value === continuation } + 1
        debug { "framesToRemove: $framesToRemove" }
        stack.dropInplace(framesToRemove)
        debug { "result stack size: ${stack.size}" }
        return completion
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