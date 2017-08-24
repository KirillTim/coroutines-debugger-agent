package kotlinx.coroutines.debug.plugin

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor

/**
 * @author Kirill Timofeev
 */
class WithAgentDefaultJavaRunner : DefaultJavaProgramRunner() {
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        println("default runner")
        return super.doExecute(state, environment)
    }
}