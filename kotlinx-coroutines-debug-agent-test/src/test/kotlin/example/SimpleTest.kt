package example

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
        TestBase.expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default", file = "Delay.kt", line = 85),
                method("example.SimpleTestMethods.defaultArgs"),
                method("example.SimpleTestMethods.defaultArgs\$default"),
                method("example.SimpleTest\$delayTest1\$result\$1.invoke")))
        TestBase.expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default", file = "Delay.kt", line = 85),
                method("example.SimpleTestMethods.tailNamedDelay"),
                method("example.SimpleTestMethods.defaultArgs"),
                method("example.SimpleTestMethods.defaultArgs\$default"),
                method("example.SimpleTest\$delayTest1\$result\$1.invoke")))
        val result = runBlocking {
            SimpleTestMethods.defaultArgs()
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }

    @Test
    fun testLambdaCreatedInside1() {
        TestBase.expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("example.SimpleTest\$testLambdaCreatedInside1\$result\$1\$lambda\$1.invoke"),
                method("example.SimpleTest\$testLambdaCreatedInside1\$result\$1.invoke")))
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
        TestBase.expectNextSuspendedState(suspended(Name("named #1"),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("example.SimpleTest\$testNamedCoroutine\$result\$1\$lambda\$1.invoke"),
                method("example.SimpleTest\$testNamedCoroutine\$result\$1.invoke")))
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
            TestBase.expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("example.SimpleTest\$testLambdaCreatedInside2\$result\$1\$lambda\$1.invoke"),
                    method("example.SimpleTest\$testLambdaCreatedInside2\$result\$1.invoke")))
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
            TestBase.expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("example.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambdaThrow\$1.invoke"),
                    method("example.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambda\$1.invoke"),
                    method("example.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")))
            TestBase.expectNextSuspendedState(suspended(Id(1),
                    method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                    method("example.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")))
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
        TestBase.expectNextSuspendedState(suspended(Id(1),
                method("kotlinx.coroutines.experimental.DelayKt.delay\$default"),
                method("example.SimpleTestMethods\$tailLambda\$1.invoke"),
                method("example.SimpleTest\$testInlineNamedSuspend\$result\$1.invoke")))
        val result = runBlocking {
            SimpleTestMethods.inlineTest(42)
        }
        Assert.assertEquals(42, result)
        expectStateEmpty()
    }
}

