@file:JvmName("EventsHandler")

package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl

/**
 * @author Kirill Timofeev
 */
// used from instrumented code
fun handleAfterSuspendCall(result: Any, continuation: Continuation<Any?>, functionCallIndex: Int) {
    if (result !== COROUTINE_SUSPENDED) return
    handleSuspendedCall(continuation, functionCallIndex)
}

private fun handleSuspendedCall(continuation: Continuation<Any?>, functionCallIndex: Int) {
    val call = allSuspendCalls[functionCallIndex]
    try {
        StacksManager.handleAfterSuspendFunctionReturn(continuation, call)
    } catch (e: Exception) {
        exceptions += e
        error {
            "handleAfterSuspendCall(${continuation.toStringSafe()}, $call) exception: ${e.stackTraceToString()}"
        }
    }
}

// used from instrumented code
fun handleDoResumeEnter(completion: Continuation<Any?>, continuation: Continuation<Any?>, doResumeIndex: Int) =
        StacksManager.handleDoResumeEnter(completion, continuation, knownDoResumeFunctions[doResumeIndex])

// used from instrumented code
fun maybeWrapCompletionAndCreateNewCoroutine(completion: Continuation<Any?>): Continuation<*> {
    if (completion is CoroutineImpl) {
        debugPrintExistingCoroutine(completion)
        return completion
    }
    val wrapped = WrappedCompletion(WeakContinuation(completion))
    debug { "wrapping completion: ${completion.prettyHash}" }
    StacksManager.handleNewCoroutineCreated(wrapped)
    return wrapped
}

private fun debugPrintExistingCoroutine(completion: CoroutineImpl) {
    val completionField = CoroutineImpl::class.java.getDeclaredField("completion")
    completionField.isAccessible = true
    debug {
        "existing coroutine with completion: " +
                "${(completionField[completion] as Continuation<*>).prettyHash}, " +
                "continuation: ${completion.prettyHash}, context: ${completion.context}"
    }
    StacksManager.ignoreNextDoResume(completion)
}
