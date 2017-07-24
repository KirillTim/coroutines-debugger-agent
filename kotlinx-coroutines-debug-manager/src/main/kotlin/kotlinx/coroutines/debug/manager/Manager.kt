package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

val doResumeToSuspendFunction = mutableMapOf<MethodId, SuspendFunction>()
private val unAssignedDoResumes = mutableMapOf<MethodId, MethodId>()
private val unAssignedSuspendFunctions = mutableSetOf<MethodId>()

val stacks = mutableMapOf<CoroutineContext, CoroutineStack>()

private fun emptyStack(context: CoroutineContext)
        = CoroutineStackImpl(context, { System.err.println("logger: \n" + this.prettyPrint()) })

//functions called from instrumented(users) code
object Manager {
    @JvmStatic
    fun afterSuspendCall(result: Any, continuation: Continuation<*>, name: String, desc: String, owner: String,
                         calledFromFunction: String, fileAndLineNumber: String) { //FIXME
        val suspended = result === COROUTINE_SUSPENDED
        val methodId = MethodIdSimple(name, owner, desc)
        val (file, lineNumber) = fileAndLineNumber.split(':')
        System.err.println("suspend call of $methodId at $file:$lineNumber with " +
                "${continuation.hashCode()} : ${if (suspended) "suspended" else "result = $result"}")
        try {
            if (suspended) {
                val func = doResumeToSuspendFunction.values.find { it.method == methodId }
                        ?: UnknownBodySuspendFunction(methodId)
                val context = continuation.context
                val stack = stacks.getOrPut(context, { emptyStack(context) })
                stack.handleSuspendFunctionReturn(continuation, FunctionCall(func, file, lineNumber.toInt(), calledFromFunction))
            }
        } catch (e: Exception) {
            System.err.println("afterSuspendCall($result, ${ObjectPrinter.objToString(continuation)}, $methodId " +
                    "at $file:$lineNumber):${e.printStackTrace()}")
        }

    }

    @JvmStatic
    fun handleDoResume(continuation: Continuation<*>, name: String, owner: String, desc: String) { //FIXME
        val methodId = MethodIdSimple(name, owner, desc)
        val func = doResumeToSuspendFunction.values.find { it.method == methodId }!! //FiXME?
        System.err.println("called doResume for $func : cont: ${continuation.hashCode()}")
        val context = continuation.context
        val stack = stacks.getOrPut(context, { emptyStack(context) })
        stack.handleDoResume(continuation, func)
    }
}

fun updateDoResumeToSuspendFunctionMap(method: MethodId) {
    if (method is MethodIdWithInfo) {
        if (method.info.isStateMachine && method.info.isAnonymous) { //is doResume for itself
            val anonymous = AnonymousSuspendFunction(method)
            doResumeToSuspendFunction += (method to anonymous)
        } else if (method.info.isSuspend) {
            val named = NamedSuspendFunction(method)
            val doResume = unAssignedDoResumes.remove(method)
            if (doResume == null) {
                unAssignedSuspendFunctions += named
            } else {
                doResumeToSuspendFunction += (doResume to named)
            }
        }
    } else if (method is DoResumeForSuspend) {
        unAssignedSuspendFunctions.find { it == method.suspend }
                ?.let {
                    doResumeToSuspendFunction += (it to method.suspend)
                    unAssignedSuspendFunctions.remove(it)
                }
    }
}