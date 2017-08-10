package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */


data class CoroutineInfo(
        val name: String,
        val additionalInfo: String,
        val thread: Thread,
        val status: CoroutineStatus,
        val stack: List<StackTraceElement>) {
    override fun toString() = buildString {
        append("\"$name\" $additionalInfo\n")
        append("  Status: $status")
        if (status == CoroutineStatus.Running) append(" on thread $thread")
        append("\n")
        stack.forEach { append("    at $it\n") }
    }
}

data class ThreadWithCoroutineInfo(
        val thread: Thread,
        val stackBeforeCoroutine: List<StackTraceElement>,
        val coroutine: CoroutineInfo)