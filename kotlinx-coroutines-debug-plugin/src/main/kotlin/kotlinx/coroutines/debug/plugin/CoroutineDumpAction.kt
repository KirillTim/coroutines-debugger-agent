package kotlinx.coroutines.debug.plugin

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.sun.jdi.StringReference
import kotlinx.coroutines.debug.plugin.coroutinedump.CoroutineDumpPanel
import kotlinx.coroutines.debug.plugin.coroutinedump.CoroutineDumpParser

/**
 * @author Kirill Timofeev
 */
class CoroutineDumpAction : AnAction(), AnAction.TransparentUpdate {
    override fun actionPerformed(e: AnActionEvent) {
        println("CoroutineDumpAction.actionPerformed($e)")
        val project = e.project ?: return
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val debuggerSession = context.debuggerSession
        if (debuggerSession == null || !debuggerSession.isAttached) return
        val process = context.debugProcess ?: return
        process.managerThread.invoke(object : DebuggerCommandImpl() {
            override fun action() {
                val vm = process.virtualMachineProxy.apply { suspend() }
                try {
                    runGetCoroutineInfoCallback(debuggerSession)
                } finally {
                    vm.resume()
                }
            }

        })
    }

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val project = e.project
        if (project == null) {
            presentation.isEnabled = false
            return
        }
        val debuggerSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
        presentation.isEnabled = debuggerSession != null && debuggerSession.isAttached
    }

    private fun runGetCoroutineInfoCallback(debuggerSession: DebuggerSession) {
        val xDebugSession = debuggerSession.xDebugSession as? XDebugSessionImpl
        val evaluator = xDebugSession?.currentStackFrame?.evaluator ?: return
        evaluator.evaluate(GET_COROUTINE_TEXT_DUMP_EXPRESSION, object : XDebuggerEvaluator.XEvaluationCallback {
            override fun errorOccurred(errorMessage: String) {
                println("error: $errorMessage")
            }

            override fun evaluated(result: XValue) {
                val textDump = ((result as JavaValue).descriptor.value as StringReference).value()
                val coroutineStates = CoroutineDumpParser.parse(textDump)
                println("coroutineStates.size = ${coroutineStates.size}")
                ApplicationManager.getApplication().invokeLater {
                    CoroutineDumpPanel.attach(xDebugSession.project, coroutineStates, xDebugSession.ui, debuggerSession)
                }
                println("result: $textDump")
            }

        }, null)
    }

    companion object {
        private val PACKAGE_NAME = "kotlinx.coroutines.debug.manager"
        private val GET_TEXT_DUMP = "getSnapshot().fullCoroutineDump().toString()"
        private val MANAGER = "StacksManager"
        private val GET_COROUTINE_TEXT_DUMP_EXPRESSION = XExpressionImpl.fromText("$PACKAGE_NAME$MANAGER$GET_TEXT_DUMP")
    }
}