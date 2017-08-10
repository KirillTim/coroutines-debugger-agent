package kotlinx.coroutines.debug.manager

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

/**
 * @author Kirill Timofeev
 */


data class CoroutineStackFrame(val continuation: Continuation<*>, val functionCall: MethodCall) {
    override fun toString() = "continuation hash: ${continuation.hashCode()}, $functionCall"
}

sealed class WrappedContext(open val context: CoroutineContext) {
    abstract val name: String
    override fun toString() = name
}

fun CoroutineContext.isSingleton() = this is EmptyCoroutineContext

fun CoroutineContext.wrap() = if (isSingleton()) SingletonContext(this) else NormalContext(this)

data class NormalContext(override val context: CoroutineContext) : WrappedContext(context) {
    override val name = "$context".substring(1, "$context".indexOf(','))
    override fun toString() = "NormalContext($context)"
}

data class SingletonContext(override val context: CoroutineContext) : WrappedContext(context) {
    private val id = nextId.getAndIncrement()
    override val name = "$context #$id"
    override fun toString() = "SingletonContext($name)"

    companion object {
        private val nextId = AtomicInteger(0)
    }
}

sealed class CoroutineStatus {
    object Running : CoroutineStatus() {
        override fun toString() = "Running"
    }

    object Suspended : CoroutineStatus() {
        override fun toString() = "Suspended"
    }
}

interface CoroutineStack {
    val context: WrappedContext
    val name: String

    fun getThread(): Thread

    fun getStatus(): CoroutineStatus

    data class Snapshot(
            val name: String,
            val context: WrappedContext,
            val status: CoroutineStatus,
            val thread: Thread,
            val stack: List<MethodCall>) {
        val coroutineInfo by lazy {
            CoroutineInfo(name, "${context.context}", thread, status, stack.map { it.stackTraceElement })
        }
    }

    fun getSnapshot(): Snapshot

    fun isEntryPoint(method: MethodId): Boolean //FIXME better signature?

    /**
     * @return true if new frames were add to stack, false otherwise
     */
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: MethodCall): Boolean

    fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend)
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

class CoroutineStackImpl private constructor(
        override val context: WrappedContext,
        private var thread: Thread,
        private val entryPoint: CoroutineStackFrame
) : CoroutineStack {
    private var status: CoroutineStatus = CoroutineStatus.Running
    private val stack = mutableListOf(entryPoint)
    private val unAppliedStack = mutableListOf<CoroutineStackFrame>()
    private var topCurrentContinuation: Continuation<*> = entryPoint.continuation

    override val name = context.name

    override fun getThread() = thread

    override fun getStatus() = status

    fun suspendedTopContinuation(continuation: Continuation<*>) =
            status == CoroutineStatus.Suspended && stack.first().continuation == continuation

    override fun getSnapshot() = synchronized(this) {
        CoroutineStack.Snapshot(name, context, status, thread, stack.map { it.functionCall })
    }

    override fun isEntryPoint(method: MethodId) = entryPoint.functionCall.method == method

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: MethodCall): Boolean {
        if (functionCall.method.name.endsWith("\$default")) {
            val delegatedCall = requireNotNull(unAppliedStack.lastOrNull(),
                    { "can't find delegated call for  $functionCall" })
            unAppliedStack.removeAt(unAppliedStack.lastIndex)
            unAppliedStack += delegatedCall.copy(functionCall = delegatedCall.functionCall.copy(
                    position = functionCall.position))
        }
        unAppliedStack.add(CoroutineStackFrame(continuation, functionCall))
        if (continuation === topCurrentContinuation && functionCall.fromMethod == stack.first().functionCall.method) {
            applyStack()
            return true
        }
        return false
    }

    private fun applyStack() {
        debug {
            buildString {
                append("stack applied\n")
                append("topCurrentContinuation hash: ${topCurrentContinuation.hashCode()}\n")
                val (cont, call) = entryPoint
                append("entry point: hash: ${cont.hashCode()}, func: $call\n")
                append("temp: ${unAppliedStack.joinToString("\n")}\n")
                append("stack: ${stack.joinToString("\n")}")
            }
        }
        status = CoroutineStatus.Suspended
        stack.addAll(0, unAppliedStack)
        topCurrentContinuation = unAppliedStack.first().continuation
        unAppliedStack.clear()
    }

    override fun handleDoResume(continuation: Continuation<*>, function: DoResumeForSuspend) {
        debug { "handleDoResumeEnter for ${function.doResume}, ${function.suspend.method} in $thread" }
        status = CoroutineStatus.Running
        thread = Thread.currentThread()
        val currentTopFrame = if (function.doResumeForItself)
            stack.indexOfLast { it.continuation === continuation && it.functionCall.method == function.doResume.method }
        else stack.indexOfLast { it.continuation === continuation } + 1
        if (currentTopFrame > 0) {
            topCurrentContinuation = continuation
            stack.dropInplace(currentTopFrame)
        }
    }

    companion object {
        fun create(
                continuation: Continuation<*>,
                entryPoint: DoResumeForSuspend,
                startedAt: Thread
        ): CoroutineStackImpl {
            val context = continuation.context.wrap()
            val entryPointCall = MethodCall(entryPoint.doResume.method,
                    entryPoint.doResumeCallPosition ?: CallPosition.UNKNOWN)
            return CoroutineStackImpl(context, startedAt, CoroutineStackFrame(continuation, entryPointCall))
        }
    }
}