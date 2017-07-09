package example

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import mylibrary.ObjectPrinter
import mylibrary.Printer

fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

/*fun main(args: Array<String>) = runBlocking<Unit> {
    Printer().print()
    val a = async(context) {
        log("I'm computing a piece of the answer")
        6
    }
    val b = async(context) {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
}*/

/*fun test(x: String) : String {
    return x + "bar"
}

fun main(args: Array<String>) {
    var x = "foo"
    println(test(x))
}*/

fun main(args: Array<String>) = runBlocking<Unit> {
    log("Started!")
    test()
    testTwoArg(1234, "str")
    testInside()
    println("Done.")
}

suspend fun testInside() {
    test()
}

suspend fun testTwoArg(time: Long, msg: String) {
    log(msg)
    test(time)
}

suspend fun test(time: Long) {
    log("test($time)")
    //delay(time)
}

suspend fun test() {
    log("test()")
    delay(1000)
}
