package kotlinx.coroutines.debug.test

import com.sun.tools.attach.VirtualMachine
import kotlinx.coroutines.debug.manager.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Kirill Timofeev
 */

sealed class CoroutineId

data class Id(val id: Int) : CoroutineId() {
    override fun toString() = "coroutine#$id"
}

data class Name(val name: String) : CoroutineId() {
    override fun toString() = name
}

data class ExpectedMethod(
        val method: String,
        val desc: String? = null,
        val file: String? = null,
        val line: Int? = null
) {
    fun isStackTraceElement(element: StackTraceElement): Boolean {
        val owner = method.split('.').dropLast(1).joinToString(".")
        val name = method.split('.').last()
        return owner == element.className
                && name == element.methodName
                && (file == null || file == element.fileName)
                && (line == null || line == element.lineNumber)
    }

    override fun toString() = buildString {
        append(method)
        if (desc != null) append(" $desc")
        if (file != null) append(" at $file")
        if (line != null) append(":$line")
    }
}

fun method(method: String, desc: String? = null, file: String? = null, line: Int? = null) =
        ExpectedMethod(method, desc, file, line)

sealed class ExpectedCoroutineState(
        open val id: CoroutineId,
        val status: CoroutineStatus,
        open val stack: List<ExpectedMethod>? = null)

data class SuspendedCoroutine(override val id: CoroutineId, override val stack: List<ExpectedMethod>)
    : ExpectedCoroutineState(id, CoroutineStatus.Suspended, stack)

data class RunningCoroutine(override val id: CoroutineId)
    : ExpectedCoroutineState(id, CoroutineStatus.Running, null)

fun suspended(id: CoroutineId, vararg stack: ExpectedMethod) = SuspendedCoroutine(id, stack.toList())

fun running(id: CoroutineId) = RunningCoroutine(id)

data class ExpectedState(val coroutines: List<ExpectedCoroutineState>) {
    private fun filterStatuses(statuses: Collection<CoroutineStatus>) =
            statuses.filter { it == CoroutineStatus.Suspended || it == CoroutineStatus.Running }

    fun assertEquals(snapshot: List<CoroutineInfo>) {
        val expectedByStateAndName = coroutines.groupBy { state -> state.status }
                .map { (status, state) -> status to state.map { "${it.id}" to it.stack }.toMap() }
                .toMap()
        val actualByStateAndName = snapshot.groupBy { it.status }
                .map { (status, state) -> status to state.map { it.name to it }.toMap() }
                .toMap()
        Assert.assertEquals(expectedByStateAndName.keys.toList(), filterStatuses(actualByStateAndName.keys).toList())
        for (status in expectedByStateAndName.keys) {
            assertEqualsForState(expectedByStateAndName[status]!!, actualByStateAndName[status]!!)
        }
    }

    private fun assertEqualsForState(
            expected: Map<String, List<ExpectedMethod>?>,
            actual: Map<String, CoroutineInfo>
    ) {
        Assert.assertEquals(expected.keys.toHashSet(), actual.keys.toHashSet())
        for ((name, expectedStack) in expected) {
            if (expectedStack == null) continue
            val actualStack = actual[name]!!.coroutineStack
            val stacksMatch = expectedStack.size == actualStack.size &&
                    expectedStack.withIndex().all { (i, element) -> element.isStackTraceElement(actualStack[i]) }
            val message = buildString {
                append("expected:\n")
                append(expectedStack.joinToString("\n"))
                append("\n")
                append("actual:\n")
                append(actualStack.joinToString("\n"))
                append("\n")
            }
            Assert.assertTrue(message, stacksMatch)
        }
    }
}

fun expectedState(vararg coroutines: ExpectedCoroutineState) = ExpectedState(coroutines.toList())

open class TestBase {

    private var actionIndex = AtomicInteger()
    private var finished = AtomicBoolean()
    private var error = AtomicReference<Throwable>()

    /**
     * Throws [IllegalStateException] like `error` in stdlib, but also ensures that the test will not
     * complete successfully even if this exception is consumed somewhere in the test.
     */
    fun error(message: Any): Nothing {
        val exception = IllegalStateException(message.toString())
        error.compareAndSet(null, exception)
        throw exception
    }

    /**
     * Throws [IllegalStateException] when `value` is false like `check` in stdlib, but also ensures that the
     * test will not complete successfully even if this exception is consumed somewhere in the test.
     */
    inline fun check(value: Boolean, lazyMessage: () -> Any): Unit {
        if (!value) error(lazyMessage())
    }

    /**
     * Asserts that this invocation is `index`-th in the execution sequence (counting from one).
     */
    fun expect(index: Int) {
        val wasIndex = actionIndex.incrementAndGet()
        check(index == wasIndex) { "Expecting action index $index but it is actually $wasIndex" }
    }

    /**
     * Asserts that this line is never executed.
     */
    fun expectUnreached() {
        error("Should not be reached")
    }

    /**
     * Asserts that this it the last action in the test. It must be invoked by any test that used [expect].
     */
    fun finish(index: Int) {
        expect(index)
        check(!finished.getAndSet(true)) { "Should call 'finish(...)' at most once" }
    }

    private var stateIndex = AtomicInteger(0)

    private val onStateChanged = { manager: StacksManager, event: StackChangedEvent, context: WrappedContext ->
        if (event == Suspended) {
            val currentState = stateIndex.getAndIncrement()
            val expected = expectedStates[currentState]
            if (expected == null) {
                println("no check for state: $currentState")
            } else {
                val snapshot = manager.getSnapshot().coroutines.map { it.coroutineInfo() }.toList()
                expected.assertEquals(snapshot)
            }

        }
    }

    @Before
    fun addCallback() {
        expectedStates.clear()
        StacksManager.addOnStackChangedCallback(onStateChanged)
    }

    @After
    fun onCompletion() {
        Assert.assertTrue("exceptions were thrown: ${exceptions.toList()}", exceptions.isEmpty())
        exceptions.clear()
        StacksManager.removeOnStackChangedCallback(onStateChanged)
        error.get()?.let { throw it }
        check(actionIndex.get() == 0 || finished.get()) { "Expecting that 'finish(...)' was invoked, but it was not" }
    }

    companion object {
        var LOG_LEVEL = "debug"
        private val AGENT_JAR_PATH = "../kotlinx-coroutines-debug-agent/build/libs/coroutines-debug-agent.jar"
        private val AGENT_ARGUMENTS = "loglevel=${LOG_LEVEL},datafile=data.all" // "datafile=" to suppress data output

        @BeforeClass
        @JvmStatic
        fun prepare() {
            loadAgent()
        }

        @JvmStatic
        private fun loadAgent() = loadAgent(AGENT_JAR_PATH, AGENT_ARGUMENTS)

        @JvmStatic
        private fun loadAgent(agentJarPath: String, agentArguments: String) {
            val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
            val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
            val vm = VirtualMachine.attach(pid)
            vm.loadAgent(agentJarPath, agentArguments)
            vm.detach()
        }

        private var expectedStates = ConcurrentHashMap<Int, ExpectedState>()
        private val nextExpectedIndex = AtomicInteger(0)

        fun expectStateEmpty() = synchronized(this) {
            Assert.assertTrue("expected empty, got: ${StacksManager.getSnapshot().coroutines}",
                    StacksManager.getSnapshot().coroutines.isEmpty())
        }

        fun expectNextSuspendedState(vararg states: ExpectedCoroutineState) = synchronized(this) {
            expectedStates[nextExpectedIndex.getAndIncrement()] = expectedState(*states)
        }
    }
}