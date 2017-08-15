package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl

/**
 * @author Kirill Timofeev
 */
class WrappedCompletion(val completion: Continuation<Any?>?) : Continuation<Any?> {
    override val context = completion!!.context

    override fun resume(value: Any?) {
        StacksManager.handleCoroutineExit(this)
        completion!!.resume(value)
    }

    override fun resumeWithException(exception: Throwable) {
        StacksManager.handleCoroutineExit(this)
        completion!!.resumeWithException(exception)
    }

    companion object {
        @JvmStatic
        fun maybeWrapCompletionAndCreateNewCoroutine(completion: Continuation<Any?>?): Continuation<*> {
            if (completion is CoroutineImpl) {
                val completionField = CoroutineImpl::class.java.getDeclaredField("completion")
                completionField.isAccessible = true
                debug {
                    "existing coroutine with completion: " +
                            "${(completionField[completion] as Continuation<*>).hashCode()}, " +
                            "continuation: ${completion.hashCode()}, context: ${completion.context}"
                }
                return completion
            }
            val wrapped = WrappedCompletion(completion)
            debug { "wrapping completion: ${completion!!.hashCode()}" }
            StacksManager.handleNewCoroutineCreated(wrapped)
            return wrapped
        }
    }
}
