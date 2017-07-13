package mylibrary

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED

/**
 * @author Kirill Timofeev
 */


/*sealed class LibrarySuspendFunction(name: String, fileName: String, lineNumber: Int)
    : SuspendFunction(name, fileName, lineNumber)

sealed class UserSuspendFunction(name: String, fileName: String, lineNumber: Int, val suspensionPoints: MutableList<SuspendFunction>)
    : SuspendFunction(name, fileName, lineNumber)*/

data class CoroutineStackFrame(val functionName: String)

object CoroutineStack {

    @JvmStatic
    fun addFunction(name: String, fileName: String, lineNumber: Int) {
        println("$name, $fileName, $lineNumber")
    }

    @JvmStatic
    fun suspensionPoints(inFunction: String, atLine: Int, functionName: String) {
        println("$inFunction, $atLine, $functionName")
    }

    val stacks = mutableMapOf<CoroutineContext, MutableList<CoroutineStackFrame>>()

    private fun getCoroutineContext(coroutineOrContinuation: Any) = when (coroutineOrContinuation) {
        is CoroutineImpl -> coroutineOrContinuation.context
        is Continuation<*> -> coroutineOrContinuation.context
        else -> throw IllegalArgumentException("expected type CoroutineImpl or Continuation got ${Any::class.java} instead")
    }

    fun stackPrettyPrint() {
        println("-----------------")
        println("Coroutine stacks:")
        for ((context, stack) in stacks) {
            println("Context: $context")
            if (stack.isEmpty()) {
                println("<empty stack>")
            } else {
                println("${stack.first().functionName}<- suspended here")
                stack.drop(1).forEach { println(it.functionName) }
            }
        }
        println("-----------------")
    }

    @JvmStatic
    fun afterSuspendCall(result: Any, coroutineOrContinuation: Any, functionName: String, calledFrom: String) {
        try {
            val suspended = result == COROUTINE_SUSPENDED
            println("suspend call of $functionName, called from $calledFrom : ${if (suspended) "suspended" else "result = $result"}")
            val context = getCoroutineContext(coroutineOrContinuation)
            if (suspended) {
                val stack = stacks[context]
                if (stack == null) {
                    //stacks[context] = mutableListOf(CoroutineStackFrame(functionName))
                } else {
                    //stacks[context] = stack
                }
                //stacks.getOrPut(context, { mutableListOf() }) += CoroutineStackFrame(functionName)
            } /*else {
                if (stacks[context]?.last() == CoroutineStackFrame(functionName)) {
                    stacks[context]!!.removeAt(stacks[context]!!.lastIndex)
                }
            }*/
            stackPrettyPrint()
        } catch (e: Exception) {
            println("afterSuspendCall($result, ${ObjectPrinter.objToString(coroutineOrContinuation)}, $functionName, $calledFrom): ")
            println(e)
        }

    }

    @JvmStatic
    fun registerCoroutineEntryPoint(coroutineOrContinuation: Any, functionName: String) {

    }

    @JvmStatic
    fun handleOnResume(coroutineOrContinuation: Any, functionName: String) {
        val context = getCoroutineContext(coroutineOrContinuation)
        val stack = stacks[context]
        if (stack == null) {
            return
        }
        for ((i, frame) in stack.withIndex()) {
            if (frame.functionName == functionName) {
                stacks[context] = stack.drop(i + 1).toMutableList()
                break
            }
        }
        stackPrettyPrint()
    }
}