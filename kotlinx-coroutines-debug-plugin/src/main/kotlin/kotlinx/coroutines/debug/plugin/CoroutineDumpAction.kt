package kotlinx.coroutines.debug.plugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * @author Kirill Timofeev
 */
class CoroutineDumpAction : AnAction(), AnAction.TransparentUpdate {
    override fun actionPerformed(e: AnActionEvent?) {
        println("CoroutineDumpAction.actionPerformed($e)")
    }

}