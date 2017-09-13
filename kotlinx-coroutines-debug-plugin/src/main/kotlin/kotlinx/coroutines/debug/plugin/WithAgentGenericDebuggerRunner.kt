package kotlinx.coroutines.debug.plugin

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor

class WithAgentGenericDebuggerRunner : GenericDebuggerRunner() {
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        PathUtil.addJavaAgentVMParam(state)
        return super.doExecute(state, environment)
    }
}