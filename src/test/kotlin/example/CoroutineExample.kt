package example

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking

fun log(msg: String) = println("[${(System.currentTimeMillis() / 1000).toString().drop(7)}: ${Thread.currentThread().name}] $msg")

//fun main(args: Array<String>) = runBlocking<Unit> {
//    val a = async(context) {
//        log("I'm computing a piece of the answer")
//        delay(1000)
//        6
//    }
//    val b = async(context) {
//        log("I'm computing another piece of the answer")
//        7
//    }
//    log("The answer is ${a.await() * b.await()}")
//}

fun main(args: Array<String>) = runBlocking<Unit> {
    log("Started!")
    /*println(1)
    testNoArgs()
    println(2)*/
    testTwoArg(1000, "str")
    /*println(3)
    testInside()*/
    println("Done.")
}

suspend fun testInside() {
    log("testInside")
    testNoArgs()
}

suspend fun oneMore(time: Long) {
    log("oneMore(${time})")
    test(time)
}

suspend fun testTwoArg(time: Long, msg: String) {
    log("testTwoArg($time, $msg)")
    oneMore(time)
    log("after test()")
    testInside()
    log("after testInside()")
}

suspend fun test(time: Long) {
    log("test($time)")
    delay(time)
    log("after test($time)")
}

suspend fun testNoArgs() {
    log("testNoArgs()")
    delay(500)
}
