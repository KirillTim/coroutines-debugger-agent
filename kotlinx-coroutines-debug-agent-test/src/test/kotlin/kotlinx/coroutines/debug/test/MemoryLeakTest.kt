package kotlinx.coroutines.debug.test

import org.junit.Assert
import org.junit.Test
import kotlin.coroutines.experimental.buildSequence

/**
 * @author Kirill Timofeev
 */

object MemoryLeakTestMethods {
    fun repeatA() = buildSequence {
        while (true) {
            yield('a')
        }
    }

    fun repeatB() = buildSequence {
        while (true) {
            yield('b')
        }
    }

    fun repeatAB() = repeatA().zip(repeatB()).map { (a, b) -> "$a$b" }

    fun createTwoGenerators() {
        val actual = repeatAB().take(2).toList()
        Assert.assertEquals(actual, listOf("ab", "ab"))
    }
}

class MemoryLeakTest : TestBase() {

    @Test
    fun abandonedCoroutinesGarbageCollected() {
        TestBase.expectNoCoroutines()
        MemoryLeakTestMethods.createTwoGenerators()
        Runtime.getRuntime().gc()
        TestBase.expectNoCoroutines()
    }
}