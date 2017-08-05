package kotlinx.coroutines.debug.manager

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val doResumeToSuspendFunctions = mutableListOf<DoResumeForSuspend>()

val suspendCalls = mutableListOf<FunctionCall>()

sealed class StackChangedEvent
class Created : StackChangedEvent()
sealed class Updated : StackChangedEvent()
class Suspended : Updated()
class WakedUp : Updated()
class Removed : StackChangedEvent()

typealias OnStackChangedCallback = StacksManager.(StackChangedEvent, CoroutineContext) -> Unit

object StacksManager {
    private val stacks = ConcurrentHashMap<CoroutineContext, CoroutineStack>()
    private val lastEvent = ConcurrentHashMap<CoroutineContext, StackChangedEvent>()
    private val changeCallbacks = mutableListOf<OnStackChangedCallback>()

    fun getStacks() = stacks.values.toList()

    fun addOnStackChangedCallback(callback: OnStackChangedCallback)
            = changeCallbacks.add(callback)

    internal fun handleAfterSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall) {
        val context = continuation.context //FIXME what to do with EmptyContext?
        val (stack, _) = createStackIfNeeded(context) //FIXME which possible entry points do we have?
        if (stack.handleSuspendFunctionReturn(continuation, functionCall)) {
            lastEvent[context] = Suspended()
            changeCallbacks.forEach { it.invoke(this, Suspended(), context) }
        }
    }

    internal fun handleDoResumeEnter(continuation: Continuation<*>, function: DoResumeForSuspend) {
        val context = continuation.context
        val (stack, created) = createStackIfNeeded(context)
        stack.handleDoResume(continuation, function)
        if (lastEvent[context] !is WakedUp) {
            val event = if (created) Created() else WakedUp()
            changeCallbacks.forEach { it.invoke(this, event, context) }
            lastEvent[context] = event
        }
    }

    internal fun handleDoResumeExit(continuation: Continuation<*>, func: DoResumeForSuspend) { //FIXME
        val context = continuation.context
        val stack = stacks[context]!!
        if (stack.isEntryPoint(func.doResume.method)) {
            stacks.remove(context)
            lastEvent.remove(context)
            changeCallbacks.forEach { it.invoke(this, Removed(), context) }
        }
    }

    internal fun handleSuspendFunctionEnter(continuation: Continuation<*>, functionCall: FunctionCall) {
        TODO("will make possible to show working(busy) coroutines")
    }

    private fun createStackIfNeeded(context: CoroutineContext): Pair<CoroutineStack, Boolean> {
        val stack = stacks[context]
        if (stack != null) return Pair(stack, false)
        val newStack = CoroutineStackImpl(context)
        stacks[context] = newStack
        return Pair(newStack, true)
    }
}

//functions called from instrumented(user) code
object InstrumentedCodeEventsHandler {
    @JvmStatic
    fun handleAfterSuspendCall(result: Any, continuation: Continuation<*>, functionCallIndex: Int) {
        val suspended = result === COROUTINE_SUSPENDED
        val call = suspendCalls[functionCallIndex]
        Logger.default.debug {
            "suspend call of ${call.function} at ${call.position.file}:${call.position.line} from ${call.fromFunction}, " +
                    "with ${continuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}"
        }
        try {
            if (suspended) {
                StacksManager.handleAfterSuspendFunctionReturn(continuation, call)
            }
        } catch (e: Exception) {
            Logger.default.error {
                "handleAfterSuspendCall($result, ${continuation.toStringSafe()}, ${call.function} " +
                        "at ${call.position.file}:${call.position.line}) exception: ${e.stackTraceToString()}"
            }
        }
    }

    @JvmStatic
    fun handleDoResumeEnter(continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val function = doResumeToSuspendFunctions[doResumeIndex]
        Logger.default.debug { "called doResume ($function) : cont: ${continuation.hashCode()}, context: ${continuation.context}" }
        StacksManager.handleDoResumeEnter(continuation, function)
    }

    @JvmStatic
    fun handleDoResumeExit(continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val function = doResumeToSuspendFunctions[doResumeIndex]
        Logger.default.debug { "exit from doResume ($function) : cont: ${continuation.hashCode()}, context: ${continuation.context}" }
        StacksManager.handleDoResumeExit(continuation, function)
    }
}