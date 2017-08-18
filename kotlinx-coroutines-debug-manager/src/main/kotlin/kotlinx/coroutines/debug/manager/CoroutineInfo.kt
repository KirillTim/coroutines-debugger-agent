package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

private fun StackTraceElement.rename(doResumeCalls: Collection<DoResumeForSuspend>): StackTraceElement {
    if (methodName != "doResume") return this
    val call = requireNotNull(doResumeCalls.find { it.doResume.method.equalsTo(this) },
            { "can't find where to map $className.$methodName" })
    val newMethodName = if (call.doResumeForItself) "invoke" else call.suspend.method.name
    return StackTraceElement(className, newMethodName, fileName, lineNumber)
}

data class Snapshot(
        val name: String,
        val context: WrappedContext,
        val status: CoroutineStatus,
        val thread: Thread,
        val coroutineStack: List<MethodCall>) {
    val threadStack = thread.stackTrace.toList()
    fun coroutineInfo(
            knownSuspendCalls: Collection<MethodCall>,
            doResumeCalls: Collection<DoResumeForSuspend>) = when (status) {
        CoroutineStatus.Created -> CreatedCoroutineInfo(name, "$context", thread)
        CoroutineStatus.Running -> {
            val (before, coroutine) = fixThreadStack(threadStack, knownSuspendCalls, doResumeCalls)
            RunningCoroutineInfo(name, "$context", thread, before, coroutine)
        }
        CoroutineStatus.Suspended -> SuspendedCoroutineInfo(name, "$context", thread,
                coroutineStack.map { it.stackTraceElement.rename(doResumeCalls) })
    }

    private fun fixThreadStack(
            threadStack: List<StackTraceElement>,
            knownSuspendCalls: Collection<MethodCall>,
            doResumeCalls: Collection<DoResumeForSuspend>): Pair<List<StackTraceElement>, List<StackTraceElement>> {
        val suspendCall: (StackTraceElement) -> Boolean = { ste ->
            knownSuspendCalls.any { it.stackTraceElement == ste } || doResumeCalls.any { it.doResume.method.equalsTo(ste) }
        }
        val stackWithoutTechnicalCalls = threadStack.filter { !it.className.startsWith(DEBUG_AGENT_PACKAGE_PREFIX) }
                .drop(1) //java.lang.Thread.getStackTrace(Thread.java:1559)
        val top = stackWithoutTechnicalCalls.indexOfFirst(suspendCall)
        if (top == -1) return Pair(stackWithoutTechnicalCalls, emptyList())
        val bottom = stackWithoutTechnicalCalls.indexOfLast(suspendCall)
        val fixedPart = stackWithoutTechnicalCalls.subList(top, bottom + 1)
                .filter(suspendCall).map { it.rename(doResumeCalls) }
        return Pair(stackWithoutTechnicalCalls.drop(bottom + 1), stackWithoutTechnicalCalls.take(top) + fixedPart)
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
        //append(stackToString(stackBeforeCoroutine)) FIXME: uncomment
    }
}

data class CreatedCoroutineInfo(
        override val name: String,
        override val additionalInfo: String,
        override val thread: Thread)
    : CoroutineInfo(name, additionalInfo, thread, CoroutineStatus.Created, emptyList()) {
    override fun toString() = header()
}