package kotlinx.coroutines.debug.plugin.coroutinedump

/**
 * @author Kirill Timofeev
 */
data class CoroutineState(val name: String, val state: String, val stack: String) {
    val stateCode = if (state == "Suspended") 0 else 1 //FIXME sealed class?
}