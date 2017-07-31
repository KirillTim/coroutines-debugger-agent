package kotlinx.coroutines.debug.manager


enum class LogLevel {
    INFO, DEBUG, ERROR
}

class Logger(val name: String = "", val level: LogLevel = LogLevel.DEBUG, val withTime: Boolean = false) {
    fun info(msg: () -> String?) {
        if (LogLevel.INFO >= level) {
            println("INFO ${build(msg)}")
        }
    }

    fun debug(msg: () -> String?) {
        if (LogLevel.DEBUG >= level) {
            println("DEBUG ${build(msg)}")
        }
    }

    fun error(msg: () -> String?) = System.err.println("ERROR ${build(msg)}")

    private inline fun build(msg: () -> String?) = "${prefix()}: ${msg.toStringSafe()}"

    private val namePrefix = if (name.isNotEmpty()) "$name:" else ""

    @Suppress("NOTHING_TO_INLINE")
    private inline fun prefix()
            = if (withTime) "[${(System.currentTimeMillis() / 1000).toString()}] " else "" + namePrefix
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun (() -> String?).toStringSafe(): String {
    try {
        return invoke().toString()
    } catch (e: Exception) {
        return "Log message invocation failed: $e"
    }
}

fun main(args: Array<String>) {
    val logger = Logger("name", withTime = true)
    logger.info { println("iside info"); "info msg" }
    logger.error { println("iside error"); "error msg" }
}