package kotlinx.coroutines.debug.test

import org.junit.Test
import java.util.*
import kotlin.system.measureNanoTime

interface Matchable {
    fun match(other: Matchable): Boolean
}

sealed class Token<out T : Matchable> {
    abstract val symbol: T
}

data class Single<out T : Matchable>(override val symbol: T) : Token<T>()
data class ZeroOrMore<out T : Matchable>(override val symbol: T) : Token<T>()
data class OneOrMore<out T : Matchable>(override val symbol: T) : Token<T>()

fun <T : Matchable> matches(pattern: List<Token<T>>, data: List<T>): Boolean {
    if (pattern.isEmpty()) return data.isEmpty()
    if (pattern.isNotEmpty() && data.isEmpty()) return false
    val head = pattern.first()
    when (head) {
        is Single -> {
            return if (head.symbol.match(data.first())) matches(pattern.drop(1), data.drop(1))
            else false
        }
        is ZeroOrMore -> {
            return if (head.symbol.match(data.first()))
                matches(pattern, data.drop(1)) || matches(pattern.drop(1), data.drop(1))
            else matches(pattern.drop(1), data)
        }
        is OneOrMore -> {
            return if (head.symbol.match(data.first()))
                matches(pattern, data.drop(1)) || matches(pattern.drop(1), data.drop(1))
            else false
        }
    }
}

class GenericRegexMatcherTest {
    private fun generateRandomSkeleton(size: Int) = (1..size).map { 'a' + Random().nextInt(10) }

    private fun generatePattern(skeleton: List<Char>)
            = skeleton.joinToString(separator = "") { "$it${if (Random().nextBoolean()) '*' else '+'}" }

    private fun generateString(skeleton: List<Char>, maxLetters: Int) =
            skeleton.joinToString(separator = "") { "$it".repeat(Random().nextInt(maxLetters) + 1) }

    private data class MatchableStr(val value: String) : Matchable {
        override fun match(other: Matchable) = other is MatchableStr && other.value == value
    }

    private fun str2Matchable(str: String): List<Token<MatchableStr>> {
        val res = mutableListOf<Token<MatchableStr>>()
        var i = 0
        while (i <= str.lastIndex) {
            if (i < str.lastIndex && str[i + 1] == '*') {
                res += ZeroOrMore(MatchableStr(str[i].toString()))
                i++
            } else if (i < str.lastIndex && str[i + 1] == '+') {
                res += OneOrMore(MatchableStr(str[i].toString()))
                i++
            } else res += Single(MatchableStr(str[i].toString()))
            i++
        }
        return res
    }

    private fun stress(size: Int, maxLetters: Int, runs: Int): Double {
        val avg = mutableListOf<Double>()
        (1..runs).forEach {
            val ske = generateRandomSkeleton(size)
            val patternStr = generatePattern(ske)
            val str = generateString(ske, maxLetters)
            val patternMy = str2Matchable(patternStr)
            val strMy = str.map { MatchableStr(it.toString()) }
            var resultRegex = false
            var timeRegex = measureNanoTime { resultRegex = patternStr.toRegex().matchEntire(str) != null }
            if (timeRegex == 0L) timeRegex = 1L
            var resultMe = false
            val timeMe = measureNanoTime { resultMe = matches(patternMy, strMy) }
            require(resultRegex == resultMe)
            avg += timeMe.toDouble() / timeRegex.toDouble()
        }
        return avg.average()
    }

    @Test
    fun stressTest() {
        println(stress(100, 100, 50))
    }
}