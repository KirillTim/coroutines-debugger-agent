package kotlinx.coroutines.debug.plugin

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor

class WithAgentDefaultJavaRunner : DefaultJavaProgramRunner() {
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        PathUtil.addJavaAgentVMParam(state)
        return super.doExecute(state, environment)
    }
}