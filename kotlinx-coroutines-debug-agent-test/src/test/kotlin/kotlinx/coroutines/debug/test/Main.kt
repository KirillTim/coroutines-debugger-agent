package kotlinx.coroutines.debug.test

import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking

fun log(msg: String) = println("[${(System.currentTimeMillis() / 1000).toString().drop(7)}: ${Thread.currentThread().name}] $msg")

/*fun main(args: Array<String>) = runBlocking<Unit> {
    val a = async(context) {
        log("I'm computing a piece of the answer")
        delay(1000)
        6
    }
    val b = async(context) {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
}*/

fun main(args: Array<String>) = runBlocking<Unit> {
    println("started")
    //log("Started!")
    oneMore()
    println(1)
    testNoArgs()
    /*println(2)
    testTwoArg(50000, "str")*/
    //println(3)
    //testInside()
    //lvl1_test()
    //testTailFirst()
    println("Done.")
}

/*suspend fun lvl1_test() {
    log("lvl1_test")
    lvl2_first_test()
    log("after lvl2_first_test()")
    lvl2_second_test()
    log("after lvl2_second_test()")
    lvl2_third_test()
    log("after lvl2_third_test()")
}

suspend fun lvl2_first_test() {
    log("lvl2_first_test")
    delay(5000)
}

suspend fun lvl2_second_test() {
    log("lvl2_second_test")
    delay(5000)
}

suspend fun lvl2_third_test() {
    log("lvl2_third_test")
    delay(5000)
}*/

suspend fun testInside() {
    log("testInside")
    testNoArgs()
}

suspend fun oneMore(time: Long = 2000L) {
    //throw IllegalArgumentException()
    log("oneMore(${time})")
    test(time)
    println("after first test($time)")
    testInside()
    println("after second test($time)")
}

suspend fun testTwoArg(time: Long): Long {
    delay(42)
    return 42
}

suspend fun testTwoArg(time: Long, msg: String) {
    /*if (time > 10000) {
        return
    }
    log("testTwoArg($time, $msg)")
    testTwoArg(2 * time, "inner $msg")*/
    //log("after testTwoArg(${2 * time}, ${"inner $msg"})")
    oneMore(time)
    log("after oneMore(${time})")
    testNoArgs()
    log("after testNoArgs()")
}

suspend fun test(time: Long) {
    log("test($time)")
    delay(time)
    log("after test($time)")
}

suspend fun testNoArgs() {
    log("testNoArgs()")
    delay(5000)
}

/*suspend fun testTailFirst() {
    return testTailSecond()
}

suspend fun testTailSecond() {
    return testTailThird()
}

suspend fun testTailThird() {
    return delay(1000)
}*/
