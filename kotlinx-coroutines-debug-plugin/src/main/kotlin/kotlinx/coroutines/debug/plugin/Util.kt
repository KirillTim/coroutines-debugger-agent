package kotlinx.coroutines.debug.plugin

import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.application.PathManager
import java.io.File

/**
 * @see [org.jetbrains.kotlin.utils.PathUtil]
 */
object PathUtil {
    private const val DEFAULT_AGENT_PARAMS = "loglevel=info,datafile="
    private const val DEBUG_AGENT_NAME = "coroutines-debug-agent.jar"

    private val AGENT_DIR_NAME = "agent"
    private val NO_PATH = File("<no_path>")

    private val JAVAAGENT_VM_PARAM = "-javaagent:${DEBUG_AGENT_PATH.absolutePath}=$DEFAULT_AGENT_PARAMS"

    fun addJavaAgentVMParam(state: RunProfileState, agentParams: String = DEFAULT_AGENT_PARAMS) {
        val javaCmdState = state as? BaseJavaApplicationCommandLineState<*>
        javaCmdState?.getJavaParameters()?.vmParametersList?.add(PathUtil.JAVAAGENT_VM_PARAM)
    }

    private val DEBUG_AGENT_PATH: File
        get() = File(File(pluginHome, AGENT_DIR_NAME), DEBUG_AGENT_NAME)

    private val pluginHome: File
        get() { //FIXME: add checks
            val jar = getResourcePathForClass(PathUtil::class.java)
            if (!jar.exists()) return NO_PATH
            val lib = jar.parentFile
            return lib.parentFile
        }

    private fun getResourcePathForClass(aClass: Class<*>): File {
        val path = "/" + aClass.name.replace('.', '/') + ".class"
        val resourceRoot = PathManager.getResourceRoot(aClass, path) ?:
                throw IllegalStateException("Resource not found: $path")
        return File(resourceRoot).absoluteFile
    }
}