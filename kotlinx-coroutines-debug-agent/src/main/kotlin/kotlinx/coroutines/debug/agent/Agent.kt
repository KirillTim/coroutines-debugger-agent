package kotlinx.coroutines.debug.agent

import kotlinx.coroutines.debug.transformer.CoroutinesDebugTransformer
import java.lang.instrument.Instrumentation
import kotlin.coroutines.experimental.CoroutineContext

/**
 * @author Kirill Timofeev
 */

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            //TODO: start server
            inst.addTransformer(CoroutinesDebugTransformer())
        }
    }
}
