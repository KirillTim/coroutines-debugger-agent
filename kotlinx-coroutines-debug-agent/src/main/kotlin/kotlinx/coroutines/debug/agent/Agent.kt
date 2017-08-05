package kotlinx.coroutines.debug.agent

import kotlinx.coroutines.debug.manager.*
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
            StacksManager.addOnStackChangedCallback { stackChangedEvent, coroutineContext ->
                if (stackChangedEvent is Updated || stackChangedEvent is Removed) {
                    Logger.default.data {
                        buildString {
                            append("event: $stackChangedEvent for context $coroutineContext\n")
                            for (stack in getStacks()) {
                                append((stack as CoroutineStackImpl).prettyPrint())
                                append("\n")
                            }
                        }
                    }
                }
            }
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
