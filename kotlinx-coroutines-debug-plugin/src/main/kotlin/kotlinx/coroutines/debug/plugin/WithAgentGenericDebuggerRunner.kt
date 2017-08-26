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

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val javaCmdState = state as? BaseJavaApplicationCommandLineState<*>
        javaCmdState?.getJavaParameters()?.vmParametersList?.add(PathUtil.JAVAAGENT_VM_PARAM)
        println("debugger runner with $javaCmdState")
        return super.doExecute(state, environment)
    }
}