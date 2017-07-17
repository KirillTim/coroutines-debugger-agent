package mylibrary

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

object CoroutineStacks {

    val stacks = mutableMapOf<CoroutineContext, CoroutineStack>()

    private fun getCoroutineContext(coroutineOrContinuation: Any) = when (coroutineOrContinuation) {
        is CoroutineImpl -> coroutineOrContinuation.context
        is Continuation<*> -> coroutineOrContinuation.context
        else -> throw IllegalArgumentException("expected type CoroutineImpl or Continuation got ${Any::class.java} instead")
    }

    @JvmStatic
    fun afterSuspendCall(result: Any, coroutineOrContinuation: Any, name: String, desc: String, owner: String, calledFrom: String) { //FIXME
        val suspended = result == COROUTINE_SUSPENDED
        val fullFunctionName = prettyPrint(owner, name, desc)
        System.err.println("suspend call of $fullFunctionName, called from $calledFrom with " +
                "${coroutineOrContinuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}")
        try {
            if (suspended) {
                val func = doResumeToSuspendFunction.values.find {
                    it.method.name == name && it.method.desc == desc
                            && it.owner.name == owner
                }
                val context = getCoroutineContext(coroutineOrContinuation)
                val stack = stacks.getOrPut(context, { CoroutineStack(context) })
                if (func != null) {
                    stack.handleSuspended(coroutineOrContinuation as Continuation<*>, func)
                } else {
                    stack.handleSuspended(coroutineOrContinuation as Continuation<*>, fullFunctionName)
                }
                System.err.println(stack.prettyPrint())
            }
        } catch (e: Exception) {
            System.err.println("afterSuspendCall($result, ${ObjectPrinter.objToString(coroutineOrContinuation)}, $fullFunctionName, $calledFrom):\n ${e.printStackTrace()}")
        }

    }

    @JvmStatic
    fun handleDoResume(coroutineOrContinuation: Any, name: String, owner: String, desc: String) { //FIXME
        val func = doResumeToSuspendFunction.values.find { it.method.name == name && it.method.desc == desc && it.owner.name == owner }!!
        System.err.println("called doResume for ${func.prettyPrint()} : cont: ${coroutineOrContinuation.hashCode()}")
        val context = getCoroutineContext(coroutineOrContinuation)
        val stack = stacks.getOrPut(context, { CoroutineStack(context) })
        stack.handleDoResume(coroutineOrContinuation as Continuation<*>, func)
    }
}