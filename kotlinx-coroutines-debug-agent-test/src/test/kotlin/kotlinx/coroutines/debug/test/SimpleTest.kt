package kotlinx.coroutines.debug.test

import kotlinx.coroutines.experimental.CoroutineName
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert
import org.junit.Test
import kotlin.coroutines.experimental.EmptyCoroutineContext

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

val TEST_PACKAGE_PREFIX = "kotlinx.coroutines.debug.test"
val COROUTINES_STDLIB_PREFIX = "kotlinx.coroutines.experimental"

class SimpleTest : TestBase() {
    @Test
    fun delayTest1() {
        expectNextSuspendedState(coroutine("coroutine#1", Suspended("$COROUTINES_STDLIB_PREFIX.DelayKt.delay")) {
            method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default", "Delay.kt", 85)
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods.defaultArgs")
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods.defaultArgs\$default")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$delayTest1\$result\$1.invoke")
        })
        expectNextSuspendedState(coroutine("coroutine#1", Suspended("$COROUTINES_STDLIB_PREFIX.DelayKt.delay")) {
            method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default", "Delay.kt", 85)
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods.tailNamedDelay")
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods.defaultArgs")
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods.defaultArgs\$default")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$delayTest1\$result\$1.invoke")
        })
        val result = runBlocking {
            SimpleTestMethods.defaultArgs()
        }
        Assert.assertEquals(42, result)
        expectNoCoroutines()
    }

    @Test
    fun testLambdaCreatedInside1() {
        expectNextSuspendedState(coroutine("coroutine#1", Suspended("$COROUTINES_STDLIB_PREFIX.DelayKt.delay")) {
            method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$testLambdaCreatedInside1\$result\$1\$lambda\$1.invoke")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$testLambdaCreatedInside1\$result\$1.invoke")
        })
        val result = runBlocking {
            val lambda: suspend () -> Unit = { delay(10) }
            lambda()
            42
        }
        Assert.assertEquals(42, result)
        expectNoCoroutines()
    }

    @Test
    fun testNamedCoroutine() {
        expectNextSuspendedState(coroutine("named#1", Suspended()) {
            method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$testNamedCoroutine\$result\$1\$lambda\$1.invoke")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$testNamedCoroutine\$result\$1.invoke")
        })
        val result = runBlocking(EmptyCoroutineContext + CoroutineName("named"), {
            val lambda: suspend () -> Unit = { delay(10) }
            lambda()
            42
        })
        Assert.assertEquals(42, result)
        expectNoCoroutines()
    }

    @Test
    fun testLambdaCreatedInside2() {
        (1..2).forEach {
            expectNextSuspendedState(coroutine("coroutine#1", Suspended()) {
                method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testLambdaCreatedInside2\$result\$1\$lambda\$1.invoke")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testLambdaCreatedInside2\$result\$1.invoke")
            })
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
        expectNoCoroutines()
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
            expectNextSuspendedState(coroutine("coroutine#1", Suspended()) {
                method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambdaThrow\$1.invoke")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testReturnFromSuspendFunction\$result\$1\$lambda\$1.invoke")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")
            })
            expectNextSuspendedState(coroutine("coroutine#1", Suspended()) {
                method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
                method("$TEST_PACKAGE_PREFIX.SimpleTest\$testReturnFromSuspendFunction\$result\$1.invoke")
            })
            try {
                lambda()
            } catch (ignore: Exception) {
            }
            delay(10)
            42
        }
        Assert.assertEquals(42, result)
        expectNoCoroutines()
    }

    @Test
    fun testThrowFromSuspendFunction() {

    }

    @Test
    fun testInlineNamedSuspend() {
        expectNextSuspendedState(coroutine("coroutine#1", Suspended()) {
            method("$COROUTINES_STDLIB_PREFIX.DelayKt.delay\$default")
            method("$TEST_PACKAGE_PREFIX.SimpleTestMethods\$tailLambda\$1.invoke")
            method("$TEST_PACKAGE_PREFIX.SimpleTest\$testInlineNamedSuspend\$result\$1.invoke")
        })
        val result = runBlocking {
            SimpleTestMethods.inlineTest(42)
        }
        Assert.assertEquals(42, result)
        expectNoCoroutines()
    }
}

