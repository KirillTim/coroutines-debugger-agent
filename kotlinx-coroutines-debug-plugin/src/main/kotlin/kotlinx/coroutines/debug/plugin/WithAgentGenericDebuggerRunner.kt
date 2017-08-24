package kotlinx.coroutines.debug.plugin

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.application.BaseJavaApplicationCommandLineState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor

/**
 * @author Kirill Timofeev
 */
class WithAgentGenericDebuggerRunner : GenericDebuggerRunner() {
    private val AGENT_PATH = "/home/user/Desktop/kotlinx.coroutines.debug/kotlinx-coroutines-debug-agent/build/libs/coroutines-debug-agent.jar"
    private val AGENT_PARAMS = "loglevel=info,datafile="

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val javaCmdState = state as? BaseJavaApplicationCommandLineState<*>
        javaCmdState?.getJavaParameters()?.vmParametersList?.add("-javaagent:$AGENT_PATH=$AGENT_PARAMS")
        println("debugger runner with $javaCmdState")
        return super.doExecute(state, environment)
    }
}