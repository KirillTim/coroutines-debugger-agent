package kotlinx.coroutines.debug.manager

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
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

val referenceQueue = ReferenceQueue<Continuation<*>>()

class WeakContinuation(continuation: Continuation<Any?>) : WeakReference<Continuation<Any?>>(continuation, referenceQueue) {
    private val hash = System.identityHashCode(continuation)
    val continuation: Continuation<Any?>? get() = get()
    override fun equals(other: Any?): Boolean = this === other || other is WeakContinuation && get() === other.get()
    override fun hashCode() = hash
}

object StacksManager {
    private val stacks = ConcurrentHashMap<WrappedContext, CoroutineStack>()
    private val topDoResumeContinuation = ConcurrentHashMap<WeakContinuation, CoroutineStack>()
    private val runningOnThread = ConcurrentHashMap<Thread, MutableList<CoroutineStack>>()
    private val initialCompletion = ConcurrentHashMap<WrappedCompletion, CoroutineStack>()
    private val ignoreDoResumeWithCompletion: MutableSet<WeakContinuation> =
            Collections.newSetFromMap(ConcurrentHashMap<WeakContinuation, Boolean>())

    private val onChangeCallbacks = CopyOnWriteArrayList<OnStackChangedCallback>()

    fun addOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.add(callback)

    fun removeOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.remove(callback)

    fun coroutinesOnThread(thread: Thread) = runningOnThread[thread]?.toList().orEmpty()

    private fun cleanupOutdatedStacks() {
        var head = referenceQueue.poll() as? WeakContinuation
        while (head != null) {
            cleanup(head)
            head = referenceQueue.poll() as? WeakContinuation
        }
    }

    private fun cleanup(weakContinuation: WeakContinuation) {
        val stack = topDoResumeContinuation.remove(weakContinuation) ?: return
        stacks.remove(stack.context)
        runningOnThread.values.forEach { it.remove(stack) }
        initialCompletion.remove(stack.initialCompletion)
        ignoreDoResumeWithCompletion.remove(weakContinuation) //should already be removed anyway
    }

    @JvmStatic
    fun getSnapshot(): FullCoroutineSnapshot {
        cleanupOutdatedStacks()
        return FullCoroutineSnapshot(stacks.values.map { it.getSnapshot() }.toList())
    }

    /**
     * Should only be called from debugger inside idea plugin
     */
    @JvmStatic
    @Suppress("unused")
    fun getFullDumpString() = getSnapshot().fullCoroutineDump(Configuration.Debug).toString()

    fun ignoreNextDoResume(completion: Continuation<Any?>) =
            ignoreDoResumeWithCompletion.add(WeakContinuation(completion))

    fun handleNewCoroutineCreated(wrappedCompletion: WrappedCompletion) {
        cleanupOutdatedStacks()
        debug { "handleNewCoroutineCreated(${wrappedCompletion.prettyHash})" }
        val stack = CoroutineStack(wrappedCompletion)
        stacks[stack.context] = stack
        initialCompletion[wrappedCompletion] = stack
        fireCallbacks(Created, stack.context)
    }

    fun handleCoroutineExit(wrappedCompletion: WrappedCompletion) {
        debug { "handleCoroutineExit(${wrappedCompletion.prettyHash})" }
        val stack = initialCompletion.remove(wrappedCompletion)!!
        stacks.remove(stack.context)
        runningOnThread[stack.thread]?.let { if (it.lastOrNull() == stack) it.dropLastInplace() }
        fireCallbacks(Removed, stack.context)
    }

    fun handleAfterSuspendFunctionReturn(continuation: Continuation<Any?>, call: SuspendCall) {
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

    fun handleDoResumeEnter(completion: Continuation<Any?>, continuation: Continuation<Any?>, function: MethodId) {
        debug { "handleDoResumeEnter(compl: ${completion.prettyHash}, cont: ${continuation.prettyHash}, $function)" }
        if (ignoreDoResumeWithCompletion.remove(WeakContinuation(completion))) {
            debug { "ignored" }
            return
        }
        topDoResumeContinuation.remove(WeakContinuation(continuation))?.let {
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
