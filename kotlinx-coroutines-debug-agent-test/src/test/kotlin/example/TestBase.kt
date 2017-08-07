package example

import com.sun.tools.attach.VirtualMachine
import org.junit.After
import org.junit.Before
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

open class TestBase {

    private val AGENT_JAR_PATH = "../kotlinx-coroutines-debug-agent/build/libs/coroutines-debug-agent.jar"
    private val AGENT_ARGUMENTS = "loglevel=info,datafile=" //no data output

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

    @Before
    fun prepare() {
        loadAgent()
    }

    private fun loadAgent() {
        val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
        val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
        val vm = VirtualMachine.attach(pid)
        vm.loadAgent(AGENT_JAR_PATH, AGENT_ARGUMENTS)
        vm.detach()
    }

    @After
    fun onCompletion() {
        error.get()?.let { throw it }
        check(actionIndex.get() == 0 || finished.get()) { "Expecting that 'finish(...)' was invoked, but it was not" }
    }
}