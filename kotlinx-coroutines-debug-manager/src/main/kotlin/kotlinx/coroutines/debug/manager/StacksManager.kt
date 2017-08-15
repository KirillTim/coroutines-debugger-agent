package kotlinx.coroutines.debug.manager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val DEBUG_AGENT_PACKAGE_PREFIX = "kotlinx.coroutines.debug"

val doResumeToSuspendFunctions = mutableListOf<DoResumeForSuspend>() //TODO concurrency

val suspendCalls = mutableListOf<MethodCall>() //TODO concurrency

sealed class StackChangedEvent(private val event: String) {
    override fun toString() = event
}

object Created : StackChangedEvent("Created")
object Removed : StackChangedEvent("Removed")
object Suspended : StackChangedEvent("Suspended")
object WakedUp : StackChangedEvent("WakedUp")

typealias OnStackChangedCallback = StacksManager.(StackChangedEvent, WrappedContext) -> Unit

object StacksManager {
    val stacks = ConcurrentHashMap<WrappedContext, CoroutineStack>()
    //map from completion(previous frame) to continuation(current frame)
    val newFrameDoResume = ConcurrentHashMap<Continuation<*>, Continuation<*>>()
    val runningCoroutines = ConcurrentHashMap<Thread, CoroutineStack>()
    val initialCompletion = ConcurrentHashMap<WrappedCompletion, CoroutineStack>()
    val bfm = ConcurrentHashMap<Continuation<*>, CoroutineStack>()
    //val doResumeContinuation = ConcurrentHashMap<Continuation<*>, CoroutineStack>()

    private val onChangeCallbacks = CopyOnWriteArrayList<OnStackChangedCallback>()

    fun addOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.add(callback)

    fun removeOnStackChangedCallback(callback: OnStackChangedCallback) = onChangeCallbacks.remove(callback)

    fun getSnapshot() = synchronized(this) {
        //TODO: remove lock?
        return@synchronized stacks.values.map { it.getSnapshot() }.toList()
    }

    fun handleNewCoroutineCreated(wrappedCompletion: WrappedCompletion) {
        debug { "handleNewCoroutineCreated(${wrappedCompletion.hashCode()})" }
        val stack = CoroutineStack(wrappedCompletion)
        stacks[stack.context] = stack
        //doResumeContinuation[stack.topContinuation] = stack
        initialCompletion[wrappedCompletion] = stack
        bfm[wrappedCompletion] = stack
        fireCallbacks(Created, stack.context)
    }

    fun handleCoroutineExit(wrappedCompletion: Continuation<*>) {
        debug { "handleCoroutineExit(${wrappedCompletion.hashCode()})" }
        val stack = initialCompletion.remove(wrappedCompletion)!!
        val keys = bfm.filter { it.value == stack }.keys
        keys.forEach { bfm.remove(it) }
        stacks.remove(stack.context)
        runningCoroutines.remove(stack.thread)
        fireCallbacks(Removed, stack.context)
    }

    fun handleAfterSuspendFunctionReturn(continuation: Continuation<*>, call: MethodCall) {
        debug { "handleAfterSuspendFunctionReturn(${continuation.hashCode()}, $call)" }
        val stack = runningCoroutines[Thread.currentThread()]!!
        //doResumeContinuation.remove(continuation)
        bfm[continuation] = stack
        if (stack.handleSuspendFunctionReturn(continuation, call)) {
            runningCoroutines.remove(stack.thread)
            //doResumeContinuation[stack.topContinuation] = stack
            fireCallbacks(Suspended, stack.context)
        }
    }

    fun handleDoResumeEnter(completion: Continuation<*>, continuation: Continuation<*>, function: DoResumeForSuspend) {
        debug { "handleDoResumeEnter(${completion.hashCode()}, ${continuation.hashCode()}, $function)" }
        val continuationSearchingFor = completion//newFrameDoResume[completion] ?: completion
        debug { "continuationSearchingFor = ${continuationSearchingFor.hashCode()}" }
        val stack = bfm[completion] ?: bfm[continuation]!!
        //val stack = doResumeContinuation.remove(continuationSearchingFor)!!
        val previousStatus = stack.status
        val nextDoResume = stack.handleDoResume(completion, continuation, function)
        //doResumeContinuation[nextDoResume] = stack
        bfm[nextDoResume] = stack
        runningCoroutines[stack.thread] = stack
        if (previousStatus != CoroutineStatus.Running) {
            fireCallbacks(WakedUp, stack.context)
        }
    }

    private fun fireCallbacks(event: StackChangedEvent, context: WrappedContext) =
            onChangeCallbacks.forEach { it.invoke(this, event, context) }
}

//functions called from instrumented(users) code
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
    fun handleDoResumeEnter(completion: Continuation<*>, continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val function = doResumeToSuspendFunctions[doResumeIndex]
        debug { "called doResume ($function) : completion: ${completion.hashCode()}, cont: ${continuation.hashCode()}, context: ${continuation.context}" }
        StacksManager.handleDoResumeEnter(completion, continuation, function)
    }
}