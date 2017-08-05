package kotlinx.coroutines.debug.agent

import kotlinx.coroutines.debug.manager.LogLevel
import kotlinx.coroutines.debug.manager.Logger
import kotlinx.coroutines.debug.transformer.CoroutinesDebugTransformer
import java.lang.instrument.Instrumentation

/**
 * @author Kirill Timofeev
 */

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            tryConfigureLogger(agentArgs)
            System.setProperty("kotlinx.coroutines.debug", "")
            //TODO: start server
            inst.addTransformer(CoroutinesDebugTransformer())
        }
    }
}

private fun tryConfigureLogger(agentArgs: String?) {
    val logLevel = agentArgs?.split(',')?.find { it.toLowerCase().startsWith("loglevel=") } ?: return
    val value = logLevel.split('=')[1]
    if (!LogLevel.values().map { it.name }.contains(value.toUpperCase())) {
        Logger.default.error { "Unknown log level '$value' in agent arguments" }
        return
    }
    Logger.default.level = LogLevel.valueOf(value.toUpperCase())
}
