package kotlinx.coroutines.debug.plugin.coroutinedump

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * @author Kirill Timofeev
 */
sealed class Status(val name: String, val code: Int, val icon: Icon) {
    companion object {
        fun byName(name: String) = when (name.toLowerCase()) {
            "suspended" -> Suspended
            "created" -> Created
            "running" -> Running
            else -> throw IllegalArgumentException("Unknown status name: $name")
        }
    }
}

object Suspended : Status("Suspended", 0, AllIcons.Debugger.ThreadStates.Paused)
object Created : Status("Created", 1, AllIcons.Debugger.ThreadStates.Paused)
object Running : Status("Running", 2, AllIcons.Debugger.ThreadStates.Running)

data class CoroutineState(val name: String, val status: Status, val stack: String, val additionalInfo: String)
