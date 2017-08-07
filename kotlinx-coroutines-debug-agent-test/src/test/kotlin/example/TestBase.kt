package example

import com.sun.tools.attach.VirtualMachine
import kotlinx.coroutines.debug.manager.StackChangedEvent
import kotlinx.coroutines.debug.manager.StacksManager
import org.junit.After
import org.junit.Before
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.experimental.CoroutineContext

data class ExpectedCall(val owner: String, val method: String, val file: String? = null, val line: Int? = null)

fun expectStacks(stacks: List<List<ExpectedCall>>) { //FIXME map from context to stack

}

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

    @Before
    fun prepare() {
        loadAgent()
        /*val clazz = Class.forName(StacksManager::class.java.name)
        val myCallback = { manager: StacksManager, event: StackChangedEvent, ctx: CoroutineContext ->
            println("my callback for $event")
        }
        StacksManager.addOnStackChangedCallback(myCallback)
        val callbacks = clazz.getDeclaredField("changeCallbacks")!!
        callbacks.isAccessible = true
        val list = callbacks[null] as List<*>
        println(list)
        println(list.size)*/
        //val callbacks = clazz.get
    }

    private fun loadAgent() {
        val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().name
        val pid = nameOfRunningVM.substring(0, nameOfRunningVM.indexOf('@'))
        val AGENT_JAR_PATH = "../build/libs/coroutines-debug-agent.jar"
        val vm = VirtualMachine.attach(pid)
        vm.loadAgent(AGENT_JAR_PATH)
        vm.detach()
    }

    @After
    fun onCompletion() {
        error.get()?.let { throw it }
        check(actionIndex.get() == 0 || finished.get()) { "Expecting that 'finish(...)' was invoked, but it was not" }
    }
}