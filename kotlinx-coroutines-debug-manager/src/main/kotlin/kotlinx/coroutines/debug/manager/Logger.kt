package kotlinx.coroutines.debug.manager

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*


enum class LogLevel {
    DEBUG, INFO, ERROR
}

sealed class LogConsumer
data class PrintStreamLogConsumer(val consumer: PrintStream) : LogConsumer()
data class FileLogConsumer(val consumer: FileOutputStream) : LogConsumer()

private interface LogConsumers {
    fun addLogConsumer(logLevel: LogLevel, consumer: PrintStream): Boolean
    fun addLogConsumer(logLevel: LogLevel, consumer: FileOutputStream): Boolean
    fun removeLogConsumer(logLevel: LogLevel, consumer: PrintStream): Boolean
    fun removeLogConsumer(logLevel: LogLevel, consumer: FileOutputStream): Boolean

}

private interface DataConsumers {
    fun addDataConsumer(consumer: PrintStream): Boolean
    fun removeDataConsumer(consumer: PrintStream): Boolean

}

class Logger(val name: String = "",
             var level: LogLevel = LogLevel.INFO, //FIXME
             val withTime: Boolean = false,
             debugConsumers: MutableList<LogConsumer> = mutableListOf(PrintStreamLogConsumer(System.out)),
             infoConsumers: MutableList<LogConsumer> = mutableListOf(PrintStreamLogConsumer(System.out)),
             errorConsumers: MutableList<LogConsumer> = mutableListOf(PrintStreamLogConsumer(System.err)),
             private val dataConsumers: HashSet<PrintStream> = hashSetOf(System.err)
) : LogConsumers, DataConsumers {
    private val logConsumers = mapOf<LogLevel, HashSet<LogConsumer>>(
            LogLevel.DEBUG to debugConsumers.toHashSet(),
            LogLevel.INFO to infoConsumers.toHashSet(),
            LogLevel.ERROR to errorConsumers.toHashSet()
    )

    fun debug(msg: () -> Any?) = message(LogLevel.DEBUG, msg)

    fun info(msg: () -> Any?) = message(LogLevel.INFO, msg)

    fun error(msg: () -> Any?) = message(LogLevel.ERROR, msg)

    fun data(msg: () -> Any?) {
        val text = "${currentTimePretty()}:\n${msg.toStringSafe()}"
        for (c in dataConsumers) {
            c.println(text)
            c.flush()
        }
    }

    private inline fun message(withLevel: LogLevel, msg: () -> Any?) {
        if (withLevel >= level) { //FIXME: can't flush all the data if writing from different levels to the same file
            val text = "${withLevel.name} ${build(msg)}"
            for (c in logConsumers[withLevel]!!) {
                val pw = when (c) {
                    is PrintStreamLogConsumer -> c.consumer
                    is FileLogConsumer -> PrintStream(c.consumer)
                }
                pw.println(text)
                pw.flush()
                if (c is FileLogConsumer) {
                    c.consumer.fd.sync()
                }
            }
        }
    }

    override fun addLogConsumer(logLevel: LogLevel, consumer: PrintStream)
            = logConsumers[logLevel]!!.add(PrintStreamLogConsumer(consumer))

    override fun addLogConsumer(logLevel: LogLevel, consumer: FileOutputStream)
            = logConsumers[logLevel]!!.add(FileLogConsumer(consumer))

    override fun removeLogConsumer(logLevel: LogLevel, consumer: PrintStream)
            = logConsumers[logLevel]!!.remove(PrintStreamLogConsumer(consumer))

    override fun removeLogConsumer(logLevel: LogLevel, consumer: FileOutputStream)
            = logConsumers[logLevel]!!.remove(FileLogConsumer(consumer))

    override fun addDataConsumer(consumer: PrintStream)
            = dataConsumers.add(consumer)

    override fun removeDataConsumer(consumer: PrintStream)
            = dataConsumers.remove(consumer)

    private inline fun build(msg: () -> Any?)
            = "${prefix()}: ${msg.toStringSafe()}"

    private val namePrefix = if (name.isNotEmpty()) "$name:" else ""

    private fun prefix()
            = if (withTime) "[${currentTimePretty()}] " else "" + namePrefix

    companion object {
        val default = Logger()
        fun logToFile(fileName: String,
                      name: String = "",
                      level: LogLevel = LogLevel.INFO,
                      withTime: Boolean = false): Logger { //FIXME
            val fos = File(fileName).outputStream()
            val logger = Logger(name, level, withTime)
            logger.removeLogConsumer(LogLevel.DEBUG, System.out)
            logger.removeLogConsumer(LogLevel.INFO, System.out)
            logger.removeLogConsumer(LogLevel.ERROR, System.err)
            logger.addLogConsumer(LogLevel.DEBUG, fos)
            logger.addLogConsumer(LogLevel.INFO, fos)
            logger.addLogConsumer(LogLevel.ERROR, fos)
            return logger
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun (() -> Any?).toStringSafe() =
        try {
            invoke().toString()
        } catch (e: Exception) {
            "Log message invocation failed: $e"
        }

private fun currentTimePretty(pattern: String = "dd.MM.yyyy HH:mm:ss.SSS")
        = SimpleDateFormat(pattern).format(Date(System.currentTimeMillis()))
