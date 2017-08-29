package kotlinx.coroutines.debug.manager

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

private sealed class FrameId {
    abstract val value: Continuation<*>
}

private data class ContinuationId(override val value: Continuation<*>) : FrameId() {
    override fun toString() = "Continuation(${value.hashCode()})"
}

private data class CompletionId(override val value: Continuation<*>) : FrameId() {
    override fun toString() = "Completion(${value.hashCode()})"
}

private data class CoroutineStackFrame(val id: FrameId, val call: MethodCall) {
    override fun toString() = "$id, $call"
}

data class WrappedContext(val name: String, val context: CoroutineContext, val additionalInfo: String? = null) {
    override fun toString() = name

    companion object {
        private val nextGeneratedId = AtomicInteger(0)
        fun fromContextWithoutId(context: CoroutineContext) =
                WrappedContext("$context$${nextGeneratedId.getAndIncrement()}", context, "")
    }
}

private fun CoroutineContext.tryGetNameAndId(): Pair<String?, Int>? {
    val parts = "$this".drop(1).dropLast(1).split(", ").map {
        val firstParenthesis = it.indexOf('(')
        if (firstParenthesis == -1) it to ""
        else it.take(firstParenthesis) to it.drop(firstParenthesis + 1).dropLast(1)
    }.toMap()
    return parts["CoroutineId"]?.let { Pair(parts["CoroutineName"], it.toInt()) }
}

private fun CoroutineContext.wrap(): WrappedContext {
    val (name, id) = tryGetNameAndId() ?: return WrappedContext.fromContextWithoutId(this)
    return WrappedContext("${name ?: "coroutine"}#$id", this)
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
    private val stack = mutableListOf<CoroutineStackFrame>()
    private val unAppliedStack = mutableListOf<CoroutineStackFrame>()

    fun getSnapshot() = CoroutineSnapshot(name, context, status, thread, stack.map { it.call })

    /**
     * @return true if new frames were add to stack, false otherwise
     */
    fun handleSuspendFunctionReturn(completion: Continuation<*>, call: SuspendCall): Boolean {
        unAppliedStack.add(CoroutineStackFrame(CompletionId(completion), call))
        if (completion === topContinuation && (call.fromMethod == stack.first().call.method
                || call.fromMethod.name == "doResume" && stack.first().call.method.name == "invoke")) {
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

    fun handleDoResume(
            completion: Continuation<*>,
            continuation: Continuation<*>,
            function: MethodId
    ): Continuation<*> {
        thread = Thread.currentThread()
        status = CoroutineStatus.Running
        if (stack.isEmpty()) {
            require(initialCompletion === completion)
            topContinuation = continuation
            stack.add(0, CoroutineStackFrame(ContinuationId(continuation),
                    DoResumeCall(function, MethodId.UNKNOWN, CallPosition.UNKNOWN)))
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
private fun MutableList<*>.dropInplace(n: Int) {
    val resultSize = size - n
    if (resultSize <= 0) clear()
    else
        while (size > resultSize)
            removeAt(0)
}