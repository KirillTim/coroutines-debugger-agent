package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val doResumeToSuspendFunctions = mutableListOf<DoResumeForSuspend>()

val stacks = mutableMapOf<CoroutineContext, CoroutineStack>()

private fun emptyStack(context: CoroutineContext)
        = CoroutineStackImpl(context, { System.err.println("logger: \n" + this.prettyPrint()) })

//functions called from instrumented(user) code
object Manager {
    @JvmStatic
    fun afterSuspendCall(result: Any, continuation: Continuation<*>, name: String, desc: String, owner: String,
                         calledFromFunction: String, fileAndLineNumber: String) { //FIXME
        val suspended = result === COROUTINE_SUSPENDED
        val methodId = MethodId(name, owner, desc)
        val (file, lineNumber) = fileAndLineNumber.split(':')
        System.err.println("suspend call of $methodId at $file:$lineNumber from $calledFromFunction, with " +
                "${continuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}")
        try {
            if (suspended) {
                val func = doResumeToSuspendFunctions.firstOrNull { it.suspend.method == methodId }?.suspend
                        ?: UnknownBodySuspendFunction(methodId)
                val context = continuation.context
                val stack = stacks.getOrPut(context, { emptyStack(context) })
                stack.handleSuspendFunctionReturn(continuation, FunctionCall(func.method, file, lineNumber.toInt(), calledFromFunction))
            }
        } catch (e: Exception) {
            System.err.println("afterSuspendCall($result, ${ObjectPrinter.objToString(continuation)}, $methodId " +
                    "at $file:$lineNumber):${e.printStackTrace()}")
        }
    }

    @JvmStatic
    fun handleDoResume(continuation: Continuation<*>, doResumeIndex: Int) { //FIXME
        val func = doResumeToSuspendFunctions[doResumeIndex]
        System.err.println("called doResume ($func) : cont: ${continuation.hashCode()}, context: ${continuation.context}")
        val context = continuation.context
        val stack = stacks.getOrPut(context, { emptyStack(context) })
        stack.handleDoResume(continuation, func)
    }
}