package kotlinx.coroutines.debug.manager

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.experimental.Continuation

/**
 * @author Kirill Timofeev
 */

val DEBUG_AGENT_PACKAGE_PREFIX = "kotlinx.coroutines.debug"

val allSuspendCalls = AppendOnlyThreadSafeList<SuspendCall>()

val knownDoResumeFunctions = AppendOnlyThreadSafeList<MethodId>()

sealed class StackChangedEvent(private val event: String) {
    override fun toString() = event
}

object Created : StackChangedEvent("Created")
object Removed : StackChangedEvent("Removed")
object Suspended : StackChangedEvent("Suspended")
object WakedUp : StackChangedEvent("WakedUp")

typealias OnStackChangedCallback = StacksManager.(StackChangedEvent, WrappedContext) -> Unit

val exceptions = AppendOnlyThreadSafeList<Exception>() //for tests and debug

object StacksManager {
    private val stacks = ConcurrentHashMap<WrappedContext, CoroutineStack>()
    private val topDoResumeContinuation = ConcurrentHashMap<Continuation<*>, CoroutineStack>()
    private val runningOnThread = ConcurrentHashMap<Thread, MutableList<CoroutineStack>>()
    private val initialCompletion = ConcurrentHashMap<WrappedCompletion, CoroutineStack>()
    private val ignoreDoResumeWithCompletion: MutableSet<Continuation<*>> =
            Collections.newSetFromMap(ConcurrentHashMap<Continuation<*>, Boolean>())

    private val onChangeCallbacks = CopyOnWriteArrayList<OnStackChangedCallback>()

    fun addOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.add(callback)

    fun removeOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.remove(callback)

    fun coroutinesOnThread(thread: Thread) = runningOnThread[thread]?.toList().orEmpty()

    @JvmStatic
    fun getSnapshot() = FullCoroutineSnapshot(stacks.values.map { it.getSnapshot() }.toList()) //TODO: concurrency

    /**
     * Should only be called from debugger inside idea plugin
     */
    @JvmStatic
    @Suppress("unused")
    fun getFullDumpString() = getSnapshot().fullCoroutineDump(Configuration.Debug).toString()

    fun ignoreNextDoResume(completion: Continuation<*>) = ignoreDoResumeWithCompletion.add(completion)

    fun handleNewCoroutineCreated(wrappedCompletion: WrappedCompletion) {
        debug { "handleNewCoroutineCreated(${wrappedCompletion.prettyHash})" }
        val stack = CoroutineStack(wrappedCompletion)
        stacks[stack.context] = stack
        initialCompletion[wrappedCompletion] = stack
        fireCallbacks(Created, stack.context)
    }

    fun handleCoroutineExit(wrappedCompletion: Continuation<*>) {
        debug { "handleCoroutineExit(${wrappedCompletion.prettyHash})" }
        val stack = initialCompletion.remove(wrappedCompletion)!!
        stacks.remove(stack.context)
        runningOnThread[stack.thread]?.let { if (it.lastOrNull() == stack) it.dropLastInplace() }
        fireCallbacks(Removed, stack.context)
    }

    fun handleAfterSuspendFunctionReturn(continuation: Continuation<*>, call: SuspendCall) {
        debug { "handleAfterSuspendFunctionReturn(${continuation.prettyHash}, $call)" }
        val runningOnCurrentThread = runningOnThread[Thread.currentThread()]!!
        val stack = runningOnCurrentThread.last()
        topDoResumeContinuation.remove(stack.topFrameCompletion)
        if (stack.handleSuspendFunctionReturn(continuation, call)) {
            runningOnCurrentThread.dropLastInplace()
            topDoResumeContinuation[stack.topFrameCompletion] = stack
            fireCallbacks(Suspended, stack.context)
        }
    }

    fun handleDoResumeEnter(completion: Continuation<*>, continuation: Continuation<*>, function: MethodId) {
        debug { "handleDoResumeEnter(compl: ${completion.prettyHash}, cont: ${continuation.prettyHash}, $function)" }
        if (ignoreDoResumeWithCompletion.remove(completion)) {
            debug { "ignored" }
            return
        }
        topDoResumeContinuation.remove(continuation)?.let {
            //first case: coroutine wake up
            debug { "coroutine waking up" }
            val previousStatus = it.status
            topDoResumeContinuation[it.handleDoResume(completion, continuation, function)] = it
            with(runningOnThread.getOrPut(it.thread, { mutableListOf() })) { if (lastOrNull() != it) add(it) }
            if (previousStatus != CoroutineStatus.Running)
                fireCallbacks(WakedUp, it.context)
            return
        }
        initialCompletion[completion]?.let {
            //second case: first lambda for coroutine
            debug { "first lambda for coroutine" }
            topDoResumeContinuation[it.handleDoResume(completion, continuation, function)] = it
            runningOnThread.getOrPut(it.thread, { mutableListOf() }).add(it)
            return
        }
        //final case: any lambda body call -> do nothing
        debug { "lambda body call" }
    }

    private fun fireCallbacks(event: StackChangedEvent, context: WrappedContext) =
            onChangeCallbacks.forEach { it.invoke(this, event, context) }
}

private fun MutableList<*>.dropLastInplace() {
    if (isNotEmpty())
        removeAt(lastIndex)
}
