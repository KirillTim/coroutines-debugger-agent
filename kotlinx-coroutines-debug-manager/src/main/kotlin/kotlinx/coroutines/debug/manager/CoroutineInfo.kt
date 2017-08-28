package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

private fun StackTraceElement.rename() =
        if (methodName == "doResume") StackTraceElement(className, "invoke", fileName, lineNumber)
        else this

data class CoroutineSnapshot(
        val name: String,
        val context: WrappedContext,
        val status: CoroutineStatus,
        val thread: Thread,
        val coroutineStack: List<MethodCall>) {
    val threadStack = thread.stackTrace.toList()
    fun coroutineInfo(knownSuspendCalls: Collection<SuspendCall> = allSuspendCalls, //FIXME?
                      knownDoResumes: Collection<MethodId> = knownDoResumeFunctions) =
            when (status) {
                CoroutineStatus.Created -> CreatedCoroutineInfo(name, context.additionalInfo, thread)
                CoroutineStatus.Running -> {
                    val (before, coroutine) = fixThreadStack(threadStack, knownSuspendCalls, knownDoResumes)
                    RunningCoroutineInfo(name, context.additionalInfo, thread, before, coroutine)
                }
                CoroutineStatus.Suspended -> SuspendedCoroutineInfo(name, context.additionalInfo, thread,
                        coroutineStack.dropLast(1).map { it.stackTraceElement.rename() })
            }

    private fun fixThreadStack(
            threadStack: List<StackTraceElement>,
            knownSuspendCalls: Collection<SuspendCall>,
            knownDoResumeCalls: Collection<MethodId>): Pair<List<StackTraceElement>, List<StackTraceElement>> {
        val suspendCall: (StackTraceElement) -> Boolean = { ste ->
            knownSuspendCalls.any { it.stackTraceElement == ste } || knownDoResumeCalls.any { it.equalsTo(ste) }
        }
        val stackWithoutTechnicalCalls = threadStack.filter { !it.className.startsWith(DEBUG_AGENT_PACKAGE_PREFIX) }
                .drop(1) //java.lang.Thread.getStackTrace(Thread.java:1559)
        val top = stackWithoutTechnicalCalls.indexOfFirst(suspendCall)
        if (top == -1) return Pair(stackWithoutTechnicalCalls, emptyList())
        val bottom = stackWithoutTechnicalCalls.indexOfLast(suspendCall)
        val fixedPart = stackWithoutTechnicalCalls.subList(top, bottom + 1).filter(suspendCall).map { it.rename() }
        return Pair(stackWithoutTechnicalCalls.drop(bottom + 1), stackWithoutTechnicalCalls.take(top) + fixedPart)
    }
}

data class FullCoroutineSnapshot(val coroutines: List<CoroutineSnapshot>) {
    fun fullCoroutineDump() = FullCoroutineDump(coroutines.map { it.coroutineInfo() })
}

data class FullCoroutineDump(private val coroutines: List<CoroutineInfo>) {
    override fun toString() = buildString {
        append(currentTimePretty("yyyy-MM-dd HH:mm:ss")) //as in thread dump
        append(" Full coroutine dump\n")
        coroutines.forEach {
            append("$it\n")
        }
    }
}

sealed class CoroutineInfo(
        open val name: String,
        open val additionalInfo: String,
        open val thread: Thread,
        val status: CoroutineStatus,
        open val coroutineStack: List<StackTraceElement>) {
    protected fun header() = buildString {
        append("\"$name\" $additionalInfo\n")
        append("  Status: $status")
        if (status == CoroutineStatus.Running) append(" on $thread")
        append("\n")
    }

    protected fun stackToString(stack: List<StackTraceElement>) = buildString {
        stack.forEach { append("    at $it\n") }
    }
}

data class SuspendedCoroutineInfo(
        override val name: String,
        override val additionalInfo: String,
        override val thread: Thread,
        override val coroutineStack: List<StackTraceElement>
) : CoroutineInfo(name, additionalInfo, thread, CoroutineStatus.Suspended, coroutineStack) {
    override fun toString() = buildString {
        //FIXME should i print last function name at the first line?
        append(header())
        append(stackToString(coroutineStack))
    }
}

data class RunningCoroutineInfo(
        override val name: String,
        override val additionalInfo: String,
        override val thread: Thread,
        val stackBeforeCoroutine: List<StackTraceElement>,
        override val coroutineStack: List<StackTraceElement>
) : CoroutineInfo(name, additionalInfo, thread, CoroutineStatus.Running, coroutineStack) {
    override fun toString() = buildString {
        append(header())
        append(stackToString(coroutineStack))
        append("    -  coroutine started\n")
        append(stackToString(stackBeforeCoroutine))
    }
}

data class CreatedCoroutineInfo(
        override val name: String,
        override val additionalInfo: String,
        override val thread: Thread)
    : CoroutineInfo(name, additionalInfo, thread, CoroutineStatus.Created, emptyList()) {
    override fun toString() = header()
}