package kotlinx.coroutines.debug.plugin

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.UnixProcessManager
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.sun.jdi.StringReference
import kotlinx.coroutines.debug.plugin.coroutinedump.CoroutineDumpPanel
import kotlinx.coroutines.debug.plugin.coroutinedump.CoroutineDumpParser
import sun.misc.Signal

class CoroutineDumpAction : AnAction(), AnAction.TransparentUpdate {
    override fun actionPerformed(e: AnActionEvent) {
        println("CoroutineDumpAction.actionPerformed($e)")
        val project = e.project ?: return
        val contentDescriptor = ExecutionManager.getInstance(project).contentManager.selectedContent
        if (contentDescriptor != null && contentDescriptor.isActiveRunSession(project)) {
            sendSignal(contentDescriptor.processHandler!!, Signal("USR2"))
            return
        }
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val debuggerSession = context.debuggerSession
        if (debuggerSession == null || !debuggerSession.isAttached) return
        val process = context.debugProcess ?: return
        process.managerThread.invoke(object : DebuggerCommandImpl() {
            override fun action() {
                val vm = process.virtualMachineProxy//.apply { suspend() }
                runGetCoroutineInfoCallback(vm, debuggerSession)
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            return
        }
        val descriptor = ExecutionManager.getInstance(project).contentManager.selectedContent
        val debuggerSessionAttached = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession?.isAttached
        //is it possible to have debuggerSession without a RunContentDescriptor?
        //FIXME: remove debuggerSession check?
        e.presentation.isEnabled = descriptor.isActiveSession || debuggerSessionAttached == true
    }

    private val RunContentDescriptor?.isActiveSession: Boolean
        get() = this != null && processHandler?.isStartNotified == true
                && processHandler?.isProcessTerminating == false
                && processHandler?.isProcessTerminated == false

    private fun RunContentDescriptor.isActiveRunSession(project: Project) =
            isActiveSession &&
                    (DebuggerManagerEx.getInstanceEx(project) as DebuggerManagerImpl)
                            .getDebugSession(processHandler) == null

    private fun sendSignal(processHandler: ProcessHandler, signal: Signal): Boolean { //FIXME: concurrency?
        if (!SystemInfo.isUnix || processHandler !is BaseOSProcessHandler) return false
        val pid = UnixProcessManager.getProcessPid(processHandler.process)
        UnixProcessManager.sendSignal(pid, signal.number)
        return true
    }

    private fun runGetCoroutineInfoCallback(vm: VirtualMachineProxyImpl, debuggerSession: DebuggerSession) {
        val xDebugSession = debuggerSession.xDebugSession as? XDebugSessionImpl
        val evaluator = xDebugSession?.currentStackFrame?.evaluator ?: return
        evaluator.evaluate(GET_COROUTINE_TEXT_DUMP_EXPRESSION, object : XDebuggerEvaluator.XEvaluationCallback {
            override fun errorOccurred(errorMessage: String) {
                println("error: $errorMessage")
                //vm.resume()
            }

            override fun evaluated(result: XValue) {
                val textDump = ((result as JavaValue).descriptor.value as StringReference).value()
                val coroutineStates = CoroutineDumpParser.parse(textDump)
                println("coroutineStates.size = ${coroutineStates.size}")
                ApplicationManager.getApplication().invokeLater {
                    CoroutineDumpPanel.attach(xDebugSession.project, coroutineStates, xDebugSession.ui, debuggerSession)
                }
                println("result: $textDump")
                //vm.resume()
            }
        }, null)
    }

    companion object {
        private const val GET_CLASS = "Class.forName(\"kotlinx.coroutines.debug.manager.StacksManager\")"
        private const val INVOKE_METHOD = ".getDeclaredMethod(\"getFullDumpString\").invoke(null)"
        private val GET_COROUTINE_TEXT_DUMP_EXPRESSION = XExpressionImpl.fromText("$GET_CLASS$INVOKE_METHOD")
    }
}