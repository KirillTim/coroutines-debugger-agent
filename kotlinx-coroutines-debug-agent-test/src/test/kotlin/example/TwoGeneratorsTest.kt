package example

import kotlinx.coroutines.experimental.yield
import org.junit.Test
import kotlin.coroutines.experimental.buildIterator
import kotlin.coroutines.experimental.buildSequence

/**
 * @author Kirill Timofeev
 */

val collection = listOf(1, 2, 3)
val wrappedCollection = object : AbstractCollection<Any>() {
    override val size: Int = collection.size + 2
    val aaa = repeatA()

    override fun iterator(): Iterator<Any> = buildIterator {
        yield(aaa.take(2).joinToString())
        yieldAll(collection)
        yield(aaa.take(3).joinToString())
    }
}

fun fibonacci() = buildSequence {
    var terms = Pair(0, 1)
    while (true) {
        yield(terms.first)
        terms = Pair(terms.second, terms.first + terms.second)
    }
}

fun repeatA() = buildSequence {
    while (true) {
        yield('a')
    }
}

class TwoGeneratorsTest : TestBase() {
    @Test
    fun testFib() {
        val fib = fibonacci()
        val allA = repeatA()
        for (f in fib) {
            if (f % 2 == 0) {
                print(allA.iterator().next())
            }
            println(f)
            if (f > 100) break
        }
    }

    @Test
    fun testSecondInside() {
        println(wrappedCollection)
    }
}

