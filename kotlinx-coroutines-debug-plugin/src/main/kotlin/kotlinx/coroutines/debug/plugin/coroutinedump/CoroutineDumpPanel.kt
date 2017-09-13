package kotlinx.coroutines.debug.plugin.coroutinedump

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.util.*
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * @see [com.intellij.unscramble.ThreadDumpPanel]
 */
class CoroutineDumpPanel(
        project: Project,
        consoleView: ConsoleView,
        toolbarActions: DefaultActionGroup,
        private val coroutineDump: List<CoroutineState>
) : JPanel(BorderLayout()) {
    private val coroutineList = JBList(DefaultListModel<CoroutineState>()).apply {
        cellRenderer = CoroutineListCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        addListSelectionListener {
            val newText = if (selectedIndex >= 0) model.getElementAt(selectedIndex).stack else ""
            AnalyzeStacktraceUtil.printStacktrace(consoleView, newText)
            repaint()
        }
    }
    private val filterField = SearchTextField().apply {
        addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                updateCoroutineList()
            }
        })
    }
    private val filterPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Filter:"), BorderLayout.WEST)
        add(filterField)
        isVisible = false
    }

    init {
        val filterAction = FilterAction().apply {
            registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).shortcutSet,
                    coroutineList)
        }
        with(toolbarActions) {
            add(filterAction)
            //add CopyToClipboardAction
            //add export to text file action
            add(SortCoroutinesAction())
        }
        add(ActionManager.getInstance().createActionToolbar("ThreadDump", toolbarActions, false).component,
                BorderLayout.WEST)

        val leftPanel = JPanel(BorderLayout()).apply {
            add(filterPanel, BorderLayout.NORTH)
            add(ScrollPaneFactory.createScrollPane(coroutineList, SideBorder.LEFT or SideBorder.RIGHT),
                    BorderLayout.CENTER)
        }

        val splitter = Splitter(false, 0.3f).apply {
            firstComponent = leftPanel
            secondComponent = consoleView.component
        }
        add(splitter, BorderLayout.CENTER)

        ListSpeedSearch(coroutineList).comparator = SpeedSearchComparator(false, true)

        updateCoroutineList()

        val editor = CommonDataKeys.EDITOR.getData(DataManager.getInstance()
                .getDataContext(consoleView.preferredFocusableComponent))
        editor?.document?.addDocumentListener(object : DocumentListener {
            override fun documentChanged(e: com.intellij.openapi.editor.event.DocumentEvent) {
                if (filterField.text.isNotEmpty())
                    highlightOccurrences(filterField.text, project, editor)
            }
        }, consoleView)
    }

    fun selectCoroutine(index: Int) {
        coroutineList.selectedIndex = index
    }

    companion object {
        fun attach(
                project: Project,
                coroutineStates: List<CoroutineState>,
                ui: RunnerLayoutUi,
                session: DebuggerSession
        ) {
            val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project).apply {
                filters(ExceptionFilters.getFilters(session.searchScope))
            }
            val consoleView = consoleBuilder.console.apply { allowHeavyFilters() }
            val toolbarActions = DefaultActionGroup()
            val panel = CoroutineDumpPanel(project, consoleView, toolbarActions, coroutineStates)

            val id = "$COROUTINE_DUMP_CONTENT_PREFIX #${currentCoroutineDumpId}"
            val content = ui.createContent(id, panel, id, null, null).apply {
                putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, java.lang.Boolean.TRUE)
                isCloseable = true
                description = "Coroutine Dump"
            }
            ui.addContent(content)
            ui.selectAndFocus(content, true, true)
            coroutineDumpsCount++
            currentCoroutineDumpId++
            Disposer.register(content, object : Disposable { //TODO report bug: called not every time if converted to lambda
                override fun dispose() {
                    coroutineDumpsCount--
                    if (coroutineDumpsCount == 0) {
                        currentCoroutineDumpId = 1
                    }
                }
            })
            Disposer.register(content, consoleView)
            ui.selectAndFocus(content, true, false)
            if (coroutineStates.isNotEmpty())
                panel.selectCoroutine(0)
        }

        private val COROUTINE_DUMP_CONTENT_PREFIX = "Coroutine Dump"
        private var currentCoroutineDumpId = 1
        private var coroutineDumpsCount = 0

        private fun getAttributes(coroutine: CoroutineState) =
                if (coroutine.status == Running) SimpleTextAttributes.REGULAR_ATTRIBUTES
                else SimpleTextAttributes.GRAY_ATTRIBUTES

        private val TYPE_LABEL = "Sort threads by type"
        private val NAME_LABEL = "Sort threads by name"
    }

    private inner class SortCoroutinesAction : DumbAwareAction(TYPE_LABEL) {
        private val BY_TYPE: (CoroutineState, CoroutineState) -> Int = { o1, o2 ->
            if (o1.status.code == o2.status.code) o1.name.compareTo(o2.name, ignoreCase = true)
            else {
                if (o1.status.code < o2.status.code) -1 else 1
            }
        }
        private val BY_NAME: (CoroutineState, CoroutineState) -> Int =
                { o1, o2 -> o1.name.compareTo(o2.name, ignoreCase = true) }
        private var COMPARATOR = BY_TYPE

        override fun actionPerformed(e: AnActionEvent) {
            Collections.sort<CoroutineState>(coroutineDump, COMPARATOR)
            updateCoroutineList()
            COMPARATOR = if (COMPARATOR === BY_TYPE) BY_NAME else BY_TYPE
            update(e)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.icon =
                    if (COMPARATOR === BY_TYPE) AllIcons.ObjectBrowser.SortByType
                    else AllIcons.ObjectBrowser.Sorted
            e.presentation.text = if (COMPARATOR === BY_TYPE) TYPE_LABEL else NAME_LABEL
        }
    }

    private inner class FilterAction : ToggleAction(
            "Filter",
            "Show only coroutines containing a specific string",
            AllIcons.General.Filter
    ), DumbAware {
        override fun isSelected(event: AnActionEvent) = filterPanel.isVisible

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            filterPanel.isVisible = state
            if (state) {
                IdeFocusManager.getInstance(AnAction.getEventProject(e)).requestFocus(filterField, true)
                filterField.selectText()
            }
            updateCoroutineList()
        }
    }

    private fun updateCoroutineList() {
        val text = if (filterPanel.isVisible) filterField.text else ""
        val model = coroutineList.model as DefaultListModel
        model.clear()
        var selectedIndex = 0
        var index = 0
        coroutineDump
                .filter { it.stack.contains(text, true) || it.name.contains(text, true) }
                .forEach {
                    model.addElement(it)
                    if (coroutineList.selectedValue === it) selectedIndex = index
                    index++
                }
        if (!model.isEmpty) coroutineList.selectedIndex = selectedIndex
        coroutineList.revalidate()
        coroutineList.repaint()
    }

    private fun highlightOccurrences(filter: String, project: Project, editor: Editor) {
        val highlightManager = HighlightManager.getInstance(project)
        val colorManager = EditorColorsManager.getInstance()
        val attributes = colorManager.globalScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES)
        val documentText = editor.document.text
        var i = -1
        while (true) {
            val nextOccurrence = documentText.indexOf(filter, i + 1, ignoreCase = true)
            if (nextOccurrence < 0) {
                break
            }
            i = nextOccurrence
            highlightManager.addOccurrenceHighlight(editor, i, i + filter.length, attributes,
                    HighlightManager.HIDE_BY_TEXT_CHANGE, null, null)
        }
    }

    private class CoroutineListCellRenderer : ColoredListCellRenderer<CoroutineState>() {
        override fun customizeCellRenderer(list: JList<out CoroutineState>,
                                           coroutineState: CoroutineState,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean
        ) {
            icon = coroutineState.status.icon
            if (!selected) background = UIUtil.getListBackground()
            val attrs = getAttributes(coroutineState)
            append(coroutineState.name + " (", attrs)
            var detail = coroutineState.status.name
            if (coroutineState.additionalInfo.isNotEmpty())
                detail += " " + coroutineState.additionalInfo
            if (detail.length > 30) detail = detail.substring(0, 30) + "..."
            append(detail, attrs)
            append(")", attrs)
        }
    }
}