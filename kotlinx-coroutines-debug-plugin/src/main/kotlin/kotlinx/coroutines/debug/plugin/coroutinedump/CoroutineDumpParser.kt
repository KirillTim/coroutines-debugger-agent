package kotlinx.coroutines.debug.plugin.coroutinedump

/**
 * @author Kirill Timofeev
 */
object CoroutineDumpParser {
    private val nameAndStatus = "^\\\"(.+)\\\"\\s.*\n {2}Status:\\s([A-Za-z]+)(.*)\n.*".toRegex()
    fun parse(coroutineDump: String) =
            coroutineDump.drop(coroutineDump.indexOf('\n') + 1).split("\n\n").map {
                val (_, name, status, additionalInfo) = nameAndStatus.find(it)?.groupValues ?: return@map null
                CoroutineState(name, Status.byName(status), it, additionalInfo.trim())
            }.filterNotNull()
}
