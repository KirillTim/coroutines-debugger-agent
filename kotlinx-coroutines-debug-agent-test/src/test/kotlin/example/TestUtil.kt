package example

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext

fun <T> handleResultContinuation(x: (T) -> Unit): Continuation<T> = object : Continuation<T> {
    override val context = EmptyCoroutineContext
    override fun resumeWithException(exception: Throwable) {
        throw exception
    }

    override fun resume(data: T) = x(data)
}

fun handleExceptionContinuation(x: (Throwable) -> Unit): Continuation<Any?> = object : Continuation<Any?> {
    override val context = EmptyCoroutineContext
    override fun resumeWithException(exception: Throwable) {
        x(exception)
    }

    override fun resume(data: Any?) {}
}

open class EmptyContinuation(override val context: CoroutineContext = EmptyCoroutineContext) : Continuation<Any?> {
    companion object : EmptyContinuation()

    override fun resume(data: Any?) {}
    override fun resumeWithException(exception: Throwable) {
        throw exception
    }
}
