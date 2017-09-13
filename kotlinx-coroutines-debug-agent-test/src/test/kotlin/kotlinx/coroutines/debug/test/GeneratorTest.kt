package kotlinx.coroutines.debug.test

import org.junit.Assert
import org.junit.Test
import kotlin.coroutines.experimental.buildSequence

object GeneratorsTestMethods {
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

    val numbers = buildSequence { var x = 1; while (true) yield(x++) }

    fun letters(c: Char, n: Int) = buildSequence {
        var x = 0;
        while (x < n) {
            yield(c); x++
        }
    }

    fun myIterator() = _myIterator(numbers)

    fun _myIterator(numbers: Sequence<Int>) = buildSequence {
        val iter = numbers.iterator()
        while (true) {
            val x = iter.next()
            yieldAll(letters(((x - 1) + 'a'.toInt()).toChar(), x))
        }
    }
}

class GeneratorsTest : TestBase() {
    val SUSPENDED_AT_YIELD = Suspended("kotlin.coroutines.experimental.SequenceBuilder.yield")
    @Test
    fun simpleGeneratorTest() {
        val N = 10
        val EXPECTED_COROUTINE = coroutine("coroutine$0", SUSPENDED_AT_YIELD) {
            method("$TEST_PACKAGE_PREFIX.GeneratorsTestMethods\$repeatA\$1.invoke")
        }
        repeat(N) { expectNextSuspendedState(EXPECTED_COROUTINE) }
        Assert.assertEquals(GeneratorsTestMethods.repeatA().take(N).toList(), (1..N).map { 'a' }.toList())
        assertDebuggerPausedHereState(EXPECTED_COROUTINE)
    }

    @Test
    fun twoGeneratorsTest() { //FIXME: coroutines numeration
        val N = 2
        expectNextSuspendedState(coroutine("coroutine$0", SUSPENDED_AT_YIELD) {
            method("$TEST_PACKAGE_PREFIX.GeneratorsTestMethods\$repeatA\$1.invoke")
        })
        val stateTwoGenerators = arrayOf(
                coroutine("coroutine$0", SUSPENDED_AT_YIELD) {
                    method("$TEST_PACKAGE_PREFIX.GeneratorsTestMethods\$repeatA\$1.invoke")
                },
                coroutine("coroutine$1", SUSPENDED_AT_YIELD) {
                    method("$TEST_PACKAGE_PREFIX.GeneratorsTestMethods\$repeatB\$1.invoke")
                })
        repeat(3) {
            expectNextSuspendedState(*stateTwoGenerators)
        }
        Assert.assertEquals(GeneratorsTestMethods.repeatAB().take(N).toList(), listOf("ab", "ab"))
        assertDebuggerPausedHereState(*stateTwoGenerators)
    }

    @Test
    fun yieldAllTest() {
        //TODO: add asserts
        val actual = GeneratorsTestMethods.myIterator().take(6).toList()
        Assert.assertEquals(actual, listOf('a', 'b', 'b', 'c', 'c', 'c'))
    }
}
