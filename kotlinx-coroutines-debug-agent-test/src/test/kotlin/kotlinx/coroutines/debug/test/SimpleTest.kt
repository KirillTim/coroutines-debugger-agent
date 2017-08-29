package kotlinx.coroutines.debug.test

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert
import org.junit.Test
import kotlin.coroutines.experimental.EmptyCoroutineContext

/**
 * @author Kirill Timofeev
 */
object SimpleTestMethods {
    suspend fun defaultArgs(time: Long = 10): Int {
        delay(time)
        tailNamedDelay()
        return 42
    }

    suspend fun tailNamedDelay() = delay(10)

    val tailLambda: suspend () -> Unit = { delay(52) }

    @Suppress("NOTHING_TO_INLINE")
    inline suspend fun inlineTest(x: Int): Int {
        println("inlineTest($x)")
        tailLambda()
        return x
    }
}

class SimpleTest : TestBase() {
    @Test
    fun delayTest1() {
        expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default", file = "Delay.kt", line = 85),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods.defaultArgs"),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods.defaultArgs\$default"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$delayTest1\$result\$1.invoke")))
        expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default", file = "Delay.kt", line = 85),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods.tailNamedDelay"),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods.defaultArgs"),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods.defaultArgs\$default"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$delayTest1\$result\$1.invoke")))
        val result = runBlocking {
            SimpleTestMethods.defaultArgs()
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testLambdaCreatedInside1() {
        expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$testLambdaCreatedInside1\$result\$1\$lambda\$1.invoke"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$testLambdaCreatedInside1\$result\$1.invoke")))
        val result = runBlocking {
            val lambda: suspend () -> Unit = { delay(10) }
            lambda()
            42
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testNamedCoroutine() {
        expectNextSuspendedState(suspended(Name("named #1"),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$testNamedCoroutine\$result\$1\$lambda\$1.invoke"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$testNamedCoroutine\$result\$1.invoke")))
        val result = runBlocking(EmptyCoroutineContext + CoroutineName("named"), {
            val lambda: suspend () -> Unit = { delay(10) }
            lambda()
            42
        })
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testLambdaCreatedInside2() {
        (1..2).forEach {
            expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testLambdaCreatedInside2\$result\$1\$lambda\$1.invoke"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testLambdaCreatedInside2\$result\$1.invoke")))
        }
        val result = runBlocking {
            val lambda: suspend () -> Unit = {
                delay(10)
            }
            lambda()
            lambda()
            42
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testReturnFromSuspendFunction() {
        val result = runBlocking {
            val lambdaThrow: suspend () -> Unit = {
                delay(10)
                throw IllegalStateException()
            }
            val lambda: suspend () -> Unit = {
                lambdaThrow()
            }
            expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambdaThrow\$1.invoke"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambda\$1.invoke"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")))
            expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("kotlinx.coroutines.debug.test.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")))
            try {
                lambda()
            } catch (ignore: Exception) {
            }
            delay(10)
            42
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testThrowFromSuspendFunction() {

    }

    @Test
    fun testInlineNamedSuspend() {
        expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("kotlinx.coroutines.debug.test.SimpleTestMethods\$tailLambda\$1.invoke"),
                method("kotlinx.coroutines.debug.test.SimpleTest\$testInlineNamedSuspend\$result\$1.invoke")))
        val result = runBlocking {
            SimpleTestMethods.inlineTest(42)
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }
}

