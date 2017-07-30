package kotlinx.coroutines.debug.agent

import kotlinx.coroutines.debug.transformer.CoroutinesDebugTransformer
import java.lang.instrument.Instrumentation

/**
 * @author Kirill Timofeev
 */

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            //TODO: start server
            //TODO setup `kotlinx.coroutines.debug` system property to true
            inst.addTransformer(CoroutinesDebugTransformer())
        }
    }
}
