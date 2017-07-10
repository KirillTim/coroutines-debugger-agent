package mylibrary

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */

sealed class SuspendFunction(val name: String, val fileName: String, val lineNumber: Int)

sealed class LibrarySuspendFunction(name: String, fileName: String, lineNumber: Int)
    : SuspendFunction(name, fileName, lineNumber)

sealed class UserSuspendFunction(name: String, fileName: String, lineNumber: Int, val suspensionPoints: MutableList<SuspendFunction>)
    : SuspendFunction(name, fileName, lineNumber)

data class CoroutineStackFrame(val functionName: String)

object CoroutineStack {
    val suspendFunctions = mutableListOf<UserSuspendFunction>()

    @JvmStatic
    fun addFunction(name: String, fileName: String, lineNumber: Int) {
        println("$name, $fileName, $lineNumber")
    }

    @JvmStatic
    fun suspensionPoints(inFunction: String, atLine: Int, functionName: String) {
        println("$inFunction, $atLine, $functionName")
    }

    val stacks = mutableMapOf<CoroutineContext, MutableList<CoroutineStackFrame>>()

    @JvmStatic
    fun afterSuspendCall(result: Any, coroutineOrContinuation: Any, functionName: String, calledFrom: String) {
        try {
            val suspended = result == COROUTINE_SUSPENDED
            println("suspend call of $functionName, called from $calledFrom : ${if (suspended) "suspended" else "result = $result"}")
            val context = if (coroutineOrContinuation is CoroutineImpl) {
                val getContextMethod = coroutineOrContinuation.javaClass.getMethod("getContext")
                getContextMethod.isAccessible = true
                getContextMethod.invoke(coroutineOrContinuation) as CoroutineContext
            } else {
                (coroutineOrContinuation as Continuation<*>).context
            }
            if (suspended) {
                stacks.getOrPut(context, { mutableListOf() }) += CoroutineStackFrame(functionName)
            } else {
                if (stacks[context]?.last() == CoroutineStackFrame(functionName)) {
                    stacks[context]!!.removeAt(stacks[context]!!.lastIndex)
                }
            }
            println("stacks state:")
            println(stacks)
        } catch (e: Exception) {
            println("afterSuspendCall($result, ${ObjectPrinter.objToString(coroutineOrContinuation)}, $functionName, $calledFrom): ")
            println(e)
        }

    }

    @JvmStatic
    fun beforeSuspendCall(coroutine: CoroutineImpl, functionName: String, from: String) {
        println("before suspend call of `$functionName` from `$from`")
    }
}