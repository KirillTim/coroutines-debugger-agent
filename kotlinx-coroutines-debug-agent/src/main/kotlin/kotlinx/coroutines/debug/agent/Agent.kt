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
            //TODO: start server
            //TODO setup `kotlinx.coroutines.debug` system property to true
            inst.addTransformer(CoroutinesDebugTransformer())
        }
    }
}

private fun tryConfigureLogger(agentArgs: String?) {
    val args = agentArgs?.split(',')
    try {
        val logkv = args?.find { it.toLowerCase().startsWith("loglevel") }
        if (logkv != null) {
            val value = logkv.split('=')[1].toUpperCase()
            Logger.default.level = LogLevel.valueOf(value)

        }
    } catch (ignore: Exception) {
    }
}
