package kotlinx.coroutines.debug.manager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val doResumeToSuspendFunctions = mutableListOf<DoResumeForSuspend>() //TODO concurrency

val suspendCalls = mutableListOf<MethodCall>() //TODO concurrency

sealed class StackChangedEvent
object Created : StackChangedEvent() {
    override fun toString() = "Created"
}

sealed class Updated : StackChangedEvent()
object Suspended : Updated() {
    override fun toString() = "Suspended"
}

object WakedUp : Updated() {
    override fun toString() = "WakedUp"
}

object Removed : StackChangedEvent() {
    override fun toString() = "Removed"
}

typealias OnStackChangedCallback = StacksManager.(StackChangedEvent, WrappedContext) -> Unit

data class StackWithEvent(val stack: CoroutineStack, val event: StackChangedEvent)

object StacksManager {
    private val stacks = ConcurrentHashMap<WrappedContext, StackWithEvent>()
    private val onChangeCallbacks = CopyOnWriteArrayList<OnStackChangedCallback>()
    private val singletonContexts = ConcurrentHashMap<SingletonContext, Thread>()

    fun addOnStackChangedCallback(callback: OnStackChangedCallback) = StacksManager.onChangeCallbacks.add(callback)

    fun getSnapshot() = synchronized(this) {
        //TODO: remove lock?
        return@synchronized stacks.values.map { it.stack.getSnapshot() }.toList()
    }

    internal fun handleAfterSuspendFunctionReturn(continuation: Continuation<*>, functionCall: MethodCall) {
        val currentThread = Thread.currentThread()
        val wrappedContext =
                if (continuation.context.isSingleton()) {
                    requireNotNull(singletonContexts.iterator().asSequence().find { it.value == currentThread }?.key, {
                        "can't find singleton context for continuation: ${continuation.hashCode()} " +
                                "started on $currentThread"
                    })
                } else continuation.context.wrap()
        val (stack, _) = stacks[wrappedContext]!!
        if (stack.handleSuspendFunctionReturn(continuation, functionCall)) {
            stacks[wrappedContext] = StackWithEvent(stack, Suspended)
            fireCallbacks(Suspended, wrappedContext)
        }
    }

    internal fun handleDoResumeEnter(continuation: Continuation<*>, function: DoResumeForSuspend) {
        val currentThread = Thread.currentThread()
        val wrappedContext =
                if (continuation.context.isSingleton()) {
                    val current = findSingletonContext(continuation) ?: SingletonContext(continuation.context)
                    singletonContexts[current] = currentThread
                    current
                } else continuation.context.wrap()
        val previous = stacks.putIfAbsent(wrappedContext,
                StackWithEvent(CoroutineStackImpl.create(continuation, function, currentThread), Created))
        if (previous == null) {
            fireCallbacks(Created, wrappedContext)
            return
        }
        previous.stack.handleDoResume(continuation, function)
        if (previous.event != WakedUp) {
            stacks[wrappedContext] = previous.copy(event = WakedUp)
            fireCallbacks(WakedUp, wrappedContext)
        }
    }

    internal fun handleDoResumeExit(continuation: Continuation<*>, func: DoResumeForSuspend) { //FIXME
        val wrappedContext = if (continuation.context.isSingleton())
            requireNotNull(findSingletonContext(continuation),
                    { "can't find singleton context for continuation: ${continuation.hashCode()}" })
        else continuation.context.wrap()
        stacks.remove(wrappedContext)
        if (wrappedContext is SingletonContext)
            singletonContexts.remove(wrappedContext)
        fireCallbacks(Removed, wrappedContext)
    }

    private fun fireCallbacks(event: StackChangedEvent, context: WrappedContext) {
        debug { "fireCallbacks($event, $context) (${onChangeCallbacks.size} listeners)" }
        if (onChangeCallbacks.isEmpty()) return
        //onChangeCallbacks.forEach { it.invoke(this, event, context) }
        thread(true) { onChangeCallbacks.forEach { it.invoke(this, event, context) } }
    }

    private fun findSingletonContext(continuation: Continuation<*>) =
            stacks.iterator().asSequence().find {
                it.key.context.isSingleton() //speedup
                        && (it.value.stack as CoroutineStackImpl).suspendedTopContinuation(continuation)
            }?.key as? SingletonContext
}

//functions called from instrumented(user) code
object InstrumentedCodeEventsHandler {
    @JvmStatic
    fun handleAfterSuspendCall(result: Any, continuation: Continuation<*>, functionCallIndex: Int) {
        val suspended = result === COROUTINE_SUSPENDED
        val call = suspendCalls[functionCallIndex]
        debug {
            "suspend call of ${call.method} at ${call.position.file}:${call.position.line} from ${call.fromMethod}, " +
                    "with ${continuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}"
        }
        try {
            if (suspended) {
                StacksManager.handleAfterSuspendFunctionReturn(continuation, call)
            }
        } catch (e: Exception) {
            error {
                "handleAfterSuspendCall($result, ${continuation.toStringSafe()}, ${call.method} " +
                        "at ${call.position.file}:${call.position.line}) exception: ${e.stackTraceToString()}"
            }
        }
    }

    @JvmStatic
    fun handleDoResumeEnter(continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val function = doResumeToSuspendFunctions[doResumeIndex]
        debug { "called doResume ($function) : cont: ${continuation.hashCode()}, context: ${continuation.context}" }
        StacksManager.handleDoResumeEnter(continuation, function)
    }

    @JvmStatic
    fun handleDoResumeExit(continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val function = doResumeToSuspendFunctions[doResumeIndex]
        debug { "exit from doResume ($function) : cont: ${continuation.hashCode()}, context: ${continuation.context}" }
        StacksManager.handleDoResumeExit(continuation, function)
    }
}