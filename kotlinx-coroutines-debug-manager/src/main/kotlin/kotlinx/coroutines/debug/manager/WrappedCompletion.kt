package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation

/**
 * @author Kirill Timofeev
 */
class WrappedCompletion(val completion: WeakContinuation) : Continuation<Any?> {
    override val context = completion.continuation!!.context

    override fun resume(value: Any?) {
        StacksManager.handleCoroutineExit(this)
        completion.continuation!!.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        StacksManager.handleCoroutineExit(this)
        completion.continuation!!.resumeWithException(exception)
    }
}
