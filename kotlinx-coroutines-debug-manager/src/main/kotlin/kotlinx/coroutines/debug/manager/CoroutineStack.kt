package kotlinx.coroutines.debug.manager

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author Kirill Timofeev
 */

private data class CoroutineStackFrame(val continuation: Continuation<*>, val functionCall: FunctionCall) {
    fun prettyPrint() = "continuation hash: ${continuation.hashCode()}, $functionCall"
}

interface CoroutineStack {
    val context: CoroutineContext
    fun getSuspendedStack(): List<FunctionCall>
    fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall)
    fun handleDoResume(continuation: Continuation<*>, function: SuspendFunction)
}

class CoroutineStackImpl(override val context: CoroutineContext,
                         val onCoroutineSuspend: CoroutineStackImpl.() -> Unit) : CoroutineStack {

    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<FunctionCall> = stack.map { it.functionCall }

    // TODO: can't handle tail recursion for suspend functions.
    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: FunctionCall) {
        val frame = CoroutineStackFrame(continuation, functionCall)
        if (functionCall.function.name.endsWith("\$default")) {
            val expectedName = functionCall.function.name.dropLast("\$default".length)
            val lastFrame = temporaryStack.lastOrNull() ?: throw IllegalStateException()
            val (cont, nonDefaultCall) = lastFrame
            if (cont != continuation) {
                throw IllegalStateException()
            }
            if (nonDefaultCall.function.name != expectedName) { //FIXME better default function check
                throw IllegalStateException("expected to see function with name $expectedName, got $nonDefaultCall instead")
            }
            val fixedLastFrame = lastFrame.copy(functionCall = nonDefaultCall.copy(line = functionCall.line))
            temporaryStack.removeAt(temporaryStack.lastIndex)
            temporaryStack.add(fixedLastFrame)
        }
        temporaryStack.add(frame)
        if (topCurrentContinuation === continuation || entryPoint?.continuation === continuation) { //can't apply without information about next suspend return
            if (stack.isEmpty()) {
                temporaryStack.add(entryPoint!!)
            }
            applyStack()
        }
    }

    private fun applyStack() {
        System.err.println("stack applied")
        System.err.println("topCurrentContinuation hash: ${topCurrentContinuation?.hashCode()}")
        System.err.println("entry point: hash: ${entryPoint?.continuation?.hashCode()}, func: ${entryPoint?.functionCall}")
        try {
            System.err.println("temp: ${temporaryStack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print temporaryStack")
        }
        try {
            System.err.println("stack: ${stack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print stack")
        }
        stack.addAll(0, temporaryStack)
        temporaryStack.clear()
        topCurrentContinuation = null
        onCoroutineSuspend()
    }

    override fun handleDoResume(continuation: Continuation<*>, function: SuspendFunction) {
        if (entryPoint == null) {
            entryPoint = CoroutineStackFrame(continuation, FunctionCall(function, function.name, -1)) //FIXME
            System.err.println("entry point: ${entryPoint?.prettyPrint()}")
        }
        var currentTopFrame = stack.indexOfFirst { it.continuation === continuation } + 1 //FIXME
        if (currentTopFrame > 0) {
            if (stack[currentTopFrame].functionCall.function.name.endsWith("\$default"))
                currentTopFrame++
            topCurrentContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
    }

    fun prettyPrint() = "$context:\n" + //FIXME
            if (stack.isEmpty()) "<empty>"
            else stack.first().prettyPrint() + " <- suspended here\n" +
                    stack.drop(1).joinToString("\n") { it.prettyPrint() } +
                    "\n--------------"

}

/*class CoroutineStackImpl(override val context: CoroutineContext,
                         val onCoroutineSuspend: CoroutineStackImpl.() -> Unit) : CoroutineStack {
    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    //will be determined by handleDoResume
    private var topCurrentSuspendFunctionCall: SuspendFunctionCall? = null //add temporary stack before this
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<SuspendFunctionCall> = stack.flatMap { it.functions }

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: SuspendFunctionCall) {
        System.err.println("continuation hash: ${continuation.hashCode()}, $functionCall")
        //Step1: add function call to last stack frame or create new
        //Step2: check if it's time to apply stack
        //          if fall to entry point (first function call is entry point itself, compare `functionCall` with second)
        //          or if topCurrentContinuation === continuation and last function call in last frame is `functionCall`

        if (topCurrentContinuation != null) { //already have something on stack
            if (topCurrentContinuation === continuation) {
                if ()
            }
        } else if (stack.isEmpty()) {
            val lastFrame = temporaryStack.lastOrNull() //order of calls in frame and in stack: top, ... , bottom
            if (lastFrame?.continuation === continuation) {
                if (functionCall.function.name.endsWith("\$default")) {
                    val nonDefaultCall = lastFrame.functions.lastOrNull()
                    val expectedName = functionCall.function.name.dropLast("\$default".length)
                    if (nonDefaultCall == null || nonDefaultCall.function.name != expectedName) { //FIXME better default function check
                        throw IllegalStateException("expected to see function with name $expectedName, got $nonDefaultCall instead")
                    }
                    val callWithCorrectLine = nonDefaultCall.copy(line = functionCall.line)
                    lastFrame.functions.removeAt(lastFrame.functions.lastIndex)
                    lastFrame.functions.add(callWithCorrectLine)
                    lastFrame.functions.add(functionCall)
                } else { //tail recursion
                    lastFrame.functions.add(functionCall)
                }
            } else { // create new frame TODO: is it possible not to have empty temporary stack here?
                temporaryStack.add(CoroutineStackFrame(continuation, mutableListOf(functionCall)))
            }
        }
    }

    private fun applyStack() {

    }

    override fun handleDoResume(continuation: Continuation<*>, function: UserDefinedSuspendFunction) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}*/


/*open class CoroutineStackImpl(override val context: CoroutineContext,
                              val onCoroutineSuspend: CoroutineStackImpl.() -> Unit) : CoroutineStack {

    private var stack = mutableListOf<CoroutineStackFrame>()
    private val temporaryStack = mutableListOf<CoroutineStackFrame>()
    private var entryPoint: CoroutineStackFrame? = null
    private var topCurrentContinuation: Continuation<*>? = null

    override fun getSuspendedStack(): List<SuspendFunctionCall> = stack.map { it.functionCall }

    override fun handleSuspendFunctionReturn(continuation: Continuation<*>, functionCall: SuspendFunctionCall) {
        val frame = CoroutineStackFrame(continuation, functionCall)
        val lastFrame = temporaryStack.lastOrNull()
        /*if (lastFrame != null && functionCall.function.name == lastFrame.functionCall.function.name + "\$default") { //FIXME
            val fixedLastFrame = lastFrame.copy(functionCall = lastFrame.functionCall.copy(line = functionCall.line))
            temporaryStack.removeAt(temporaryStack.lastIndex)
            temporaryStack.add(fixedLastFrame)
        }*/
        temporaryStack.add(frame)

        if (topCurrentContinuation != null) { //already have something on stack
            if (topCurrentContinuation === continuation && functionCall.function is UserDefinedSuspendFunction
                    && !functionCall.function.name.endsWith("\$default")) {
                applyStack()
            }
        } else if (stack.isEmpty()) {
            if (entryPoint?.continuation === frame.continuation && !functionCall.function.name.endsWith("\$default")) {
                temporaryStack.add(entryPoint!!)
                applyStack()
            }
        }
    }

    // TODO: can't handle tail recursion for suspend functions.
// map from continuation to first suspend functionCall call needed to restore different calls with same continuation
    private fun applyStack() {
        System.err.println("stack applied")
        System.err.println("topCurrentContinuation hash: ${topCurrentContinuation?.hashCode()}")
        System.err.println("entry point: hash: ${entryPoint?.continuation?.hashCode()}, func: ${entryPoint?.functionCall}")
        try {
            System.err.println("temp: ${temporaryStack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print temporaryStack")
        }
        try {
            System.err.println("stack: ${stack.joinToString("\n", transform = { it.prettyPrint() })}")
        } catch (e: Exception) {
            System.err.println("can't  print stack")
        }
        stack.addAll(0, temporaryStack)
        temporaryStack.clear()
        topCurrentContinuation = null
        onCoroutineSuspend()
    }

    override fun handleDoResume(continuation: Continuation<*>, function: UserDefinedSuspendFunction) {
        if (entryPoint == null) {
            entryPoint = CoroutineStackFrame(continuation, SuspendFunctionCall(function, "unknown", -1))
            System.err.println("entry point: ${entryPoint?.prettyPrint()}")
        }
        val currentTopFrame = stack.indexOfFirst {
            !it.functionCall.function.name.endsWith("\$default") &&
                    it.continuation === continuation
        } + 1 //FIXME
        if (currentTopFrame > 0) {
            topCurrentContinuation = continuation
            stack = stack.drop(currentTopFrame).toMutableList()
        }
    }

    fun prettyPrint() = "$context:\n" + //FIXME
            if (stack.isEmpty()) "<empty>"
            else stack.first().prettyPrint() + " <- suspended here\n" +
                    stack.drop(1).joinToString("\n") { it.prettyPrint() } +
                    "\n--------------"
}*/