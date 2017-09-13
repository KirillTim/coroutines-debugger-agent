package kotlinx.coroutines.debug.test

import com.sun.tools.attach.VirtualMachine
import kotlinx.coroutines.debug.manager.*
import kotlinx.coroutines.debug.manager.Suspended
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ExpectedState(val coroutines: List<Coroutine>)

private val NAME_AND_STATUS_FROM_COROUTINE_DUMP =
        "^\\\"(.+)\\\".*\n {2}Status:\\s([A-Za-z]+).*\n.*".toRegex(RegexOption.DOT_MATCHES_ALL)

private fun extractNameFromCoroutineDump(dump: String): String {
    val (_, name, _) = requireNotNull(NAME_AND_STATUS_FROM_COROUTINE_DUMP.find(dump)?.groupValues,
            { "Can't extract name and status from '$dump'" })
    return name
}

fun assertDebuggerPausedHereState(vararg expected: Coroutine) =
        assertMatches(expected.toList(), extractFixedCoroutineDumpsAsInDebugger())

val TEST_BASE_CLASS_NAME = "kotlinx.coroutines.debug.test.TestBaseKt"

typealias CoroutineName = String
typealias Dump = String
private fun extractFixedCoroutineDumpsAsInDebugger(): Map<CoroutineName, Dump> {
    val dump = likeInDebugTextStateDump()
    return dump.substring(dump.indexOf('\n') + 1) //drop header
            .split("\n\n").dropLast(1).map {
        var (currentDump, name, status) = requireNotNull(NAME_AND_STATUS_FROM_COROUTINE_DUMP.find(it)?.groupValues,
                { "Can't parse coroutine dump:\n $dump" })
        if (status.toLowerCase() == "running") {
            val parts = currentDump.split('\n')
            var index = 2 //top stack frame
            while (parts[index].startsWith("    at $TEST_BASE_CLASS_NAME")) {
                index++
            }
            currentDump = (parts.take(2) + parts.drop(index)).joinToString("\n")
        }
        name to currentDump
    }.toMap()
}

private fun likeInDebugTextStateDump(): String =
        Class.forName("kotlinx.coroutines.debug.manager.StacksManager")
                .getDeclaredMethod("getFullDumpString").invoke(null) as String

private fun assertMatches(expected: List<Coroutine>, actual: Map<CoroutineName, Dump>) {
    Assert.assertTrue("expected: ${expected.map { it.name }}, got: ${actual.keys}",
            expected.map { it.name }.toSet() == actual.keys.toSet())
    for (coroutine in expected) {
        val dump = actual[coroutine.name]!!.trim('\n')
        val message = buildString {
            append(coroutine.pattern.joinToString("\n", "expected:\n", "\n") { it.symbol.string })
            append("actual:\n$dump")
        }
        Assert.assertTrue(message, coroutine.matchEntireString(dump))
    }
}

open class TestBase {
    private var stateIndex = AtomicInteger(0)

    private val onStateChanged = { manager: StacksManager, event: StackChangedEvent, _: WrappedContext ->
        if (event == Suspended && expectedStates.isNotEmpty()) {
            val currentState = stateIndex.getAndIncrement()
            val expected = expectedStates[currentState]
            if (expected == null) println("no check for state: $currentState")
            else {
                val actual = manager.getSnapshot().coroutines.filter { it.status is CoroutineStatus.Suspended }
                        .map { it.coroutineInfo(Thread.currentThread(), Configuration.Debug).toString() }
                        .map { extractNameFromCoroutineDump(it) to it }.toMap()
                assertMatches(expected.coroutines, actual)
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
    }

    companion object {
        var LOG_LEVEL = "error"//"debug"
        private val AGENT_JAR_PATH = "../kotlinx-coroutines-debug-agent/build/libs/coroutines-debug-agent.jar"
        private val AGENT_ARGUMENTS = "loglevel=${LOG_LEVEL}"

        @BeforeClass
        @JvmStatic
        fun prepare() {
            loadAgent()
        }

        @JvmStatic
        private fun loadAgent(agentJarPath: String = AGENT_JAR_PATH, agentArguments: String = AGENT_ARGUMENTS) {
            val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
            val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
            val vm = VirtualMachine.attach(pid)
            vm.loadAgent(agentJarPath, agentArguments)
            vm.detach()
        }

        private var expectedStates = ConcurrentHashMap<Int, ExpectedState>()
        private val nextExpectedIndex = AtomicInteger(0)

        fun expectNoCoroutines(customFilter: CoroutineSnapshot.() -> Boolean = { true }) {
            val coroutines = StacksManager.getSnapshot().coroutines.filter(customFilter)
            Assert.assertTrue("expected nothing, got: $coroutines", coroutines.isEmpty())
        }

        fun expectNoSuspendCoroutines() = expectNoCoroutines({ status is CoroutineStatus.Suspended })

        fun expectNextSuspendedState(vararg coroutines: Coroutine) {
            expectedStates[nextExpectedIndex.getAndIncrement()] = ExpectedState(coroutines.toList())
        }
    }
}