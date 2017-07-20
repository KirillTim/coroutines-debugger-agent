@file:JvmName("CoroutinesManager")

package mylibrary

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val stacks = mutableMapOf<CoroutineContext, CoroutineStackImpl>()

fun afterSuspendCall(result: Any, continuation: Continuation<*>, name: String, desc: String, owner: String, calledFrom: String) { //FIXME
    val suspended = result === COROUTINE_SUSPENDED
    val fullFunctionName = prettyPrint(owner, name, desc)
    System.err.println("suspend call of $fullFunctionName, called from $calledFrom with " +
            "${continuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}")
    try {
        if (suspended) {
            val func = doResumeToSuspendFunction.values.find {
                it.method.name == name && it.method.desc == desc
                        && it.owner.name == owner
            } ?: LibrarySuspendFunction(name, owner, desc)
            val context = continuation.context
            val stack = stacks.getOrPut(context, { CoroutineStackImpl(context, { System.err.println("logger: \n" + this.prettyPrint()) }) })
            stack.handleSuspendFunctionReturn(continuation, func)
        }
    } catch (e: Exception) {
        System.err.println("afterSuspendCall($result, ${ObjectPrinter.objToString(continuation)}, $fullFunctionName, $calledFrom):\n ${e.printStackTrace()}")
    }

}

fun handleDoResume(continuation: Continuation<*>, name: String, owner: String, desc: String) { //FIXME
    val func = doResumeToSuspendFunction.values.find { it.method.name == name && it.method.desc == desc && it.owner.name == owner }!!
    System.err.println("called doResume for ${func.prettyPrint()} : cont: ${continuation.hashCode()}")
    val context = continuation.context
    val stack = stacks.getOrPut(context, { CoroutineStackImpl(context, { System.err.println("logger: \n" + this.prettyPrint()) }) })
    stack.handleDoResume(continuation, func)
}