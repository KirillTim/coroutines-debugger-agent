package example

import org.junit.Assert
import org.junit.Test
import kotlin.coroutines.experimental.buildSequence

/**
 * @author Kirill Timofeev
 */
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
    @Test
    fun simpleGeneratorTest() {
        val N = 10
        (1..N).forEach {
            TestBase.expectNextSuspendedState(suspended(Name("EmptyCoroutineContext #0"),
                    method("example.GeneratorsTestMethods\$repeatA\$1.invoke")))
        }
        Assert.assertEquals(GeneratorsTestMethods.repeatA().take(N).toList(), (1..N).map { 'a' }.toList())
    }

    @Test
    fun twoGeneratorsTest() {
        val N = 2
        TestBase.expectNextSuspendedState(
                suspended(Name("EmptyCoroutineContext #0"),
                        method("example.GeneratorsTestMethods\$repeatA\$1.invoke")))
        val stateTwoGenerators = arrayOf<ExpectedCoroutineState>(
                suspended(Name("EmptyCoroutineContext #0"),
                        method("example.GeneratorsTestMethods\$repeatA\$1.invoke")),
                suspended(Name("EmptyCoroutineContext #1"),
                        method("example.GeneratorsTestMethods\$repeatB\$1.invoke")))
        repeat(3) {
            TestBase.expectNextSuspendedState(*stateTwoGenerators)
        }
        Assert.assertEquals(GeneratorsTestMethods.repeatAB().take(N).toList(), listOf("ab", "ab"))
        TestBase.expectNextSuspendedState(*stateTwoGenerators)
    }

    @Test
    fun yieldAllTest() {
        TestBase.expectNextSuspendedState(running(Name("EmptyCoroutineContext #0")),
                suspended(Name("EmptyCoroutineContext #1"), method("example.GeneratorsTestMethods\$numbers\$1.invoke")))
        //TODO: add asserts for next states
        val actual = GeneratorsTestMethods.myIterator().take(6).toList()
        Assert.assertEquals(actual, listOf('a', 'b', 'b', 'c', 'c', 'c'))
    }
}
