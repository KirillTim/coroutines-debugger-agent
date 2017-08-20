package kotlinx.coroutines.debug.agent

import kotlinx.coroutines.debug.manager.*
import kotlinx.coroutines.debug.transformer.CoroutinesDebugTransformer
import java.io.FileOutputStream
import java.lang.instrument.Instrumentation

/**
 * @author Kirill Timofeev
 */

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            agentSetup(agentArgs, inst)
            info { "called Agent.premain($agentArgs, $inst)" }
        }

        @JvmStatic
        fun agentmain(agentArgs: String?, inst: Instrumentation) {
            agentSetup(agentArgs, inst)
            info { "called Agent.agentmain($agentArgs, $inst)" }
        }

        private fun agentSetup(agentArgs: String?, inst: Instrumentation) {
            tryConfigureLogger(agentArgs)
            System.setProperty("kotlinx.coroutines.debug", "")
            startServerIfNeeded(agentArgs)
            if (Logger.config.dataConsumers.isNotEmpty()) {
                StacksManager.addOnStackChangedCallback { event, coroutineContext ->
                    if (event == Created || event == Suspended || event == Removed)
                        data {
                            buildString {
                                append("event: $event for context $coroutineContext\n")
                                append("snapshot:\n")
                                getSnapshot().forEach {
                                    append("${it.coroutineInfo(allSuspendCalls, allDoResumeCalls)}\n")
                                }
                            }
                        }
                }
            }
            inst.addTransformer(CoroutinesDebugTransformer())
        }
    }
}

private fun startServerIfNeeded(agentArgs: String?) {
    //TODO
}

private fun tryConfigureLogger(agentArgs: String?) {
    val levelValue = agentArgs?.split(',')?.find { it.toLowerCase().startsWith("loglevel=") }?.split('=')?.get(1)
    val logLevel = levelValue?.let {
        if (!LogLevel.values().map { it.name }.contains(levelValue.toUpperCase())) {
            error { "Unknown log level '$levelValue' in agent arguments" }
            LogLevel.INFO
        } else LogLevel.valueOf(levelValue.toUpperCase())
    } ?: LogLevel.INFO
    val logFileValue = agentArgs?.split(',')?.find { it.toLowerCase().startsWith("logfile=") }?.split('=')?.get(1)
    val logFileOutputStream = logFileValue?.let { FileOutputStream(it) }
    val dataFileValue = agentArgs?.split(',')?.find { it.toLowerCase().startsWith("datafile=") }?.split('=')?.get(1)
    val dataFileOutputStreams = listOfNotNull(//'datafile=' argument supress data output (data {...} )
            if (dataFileValue == logFileValue) logFileOutputStream
            else dataFileValue?.let {
                try {
                    FileOutputStream(it)
                } catch (e: Exception) {
                    null
                }
            }
    )
    Logger.config = if (logFileOutputStream != null) {
        logToFile(logLevel, withTime = true, logFileOutputStream = logFileOutputStream,
                dataConsumers = dataFileOutputStreams)
    } else {
        if (dataFileOutputStreams.isNotEmpty() || dataFileValue != null)
            LoggerConfig(logLevel, withTime = true, dataConsumers = dataFileOutputStreams)
        else LoggerConfig(logLevel)
    }
}

