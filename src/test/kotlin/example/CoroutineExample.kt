package example

import kotlinx.coroutines.experimental.*

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
    println("after first test($time)")
    testInside()
    println("after second test($time)")
}

suspend fun testTwoArg(time: Long, msg: String) {
    if (time > 10000) {
        return
    }
    log("testTwoArg($time, $msg)")
    testTwoArg(2 * time, "inner $msg")
    log("after testTwoArg(${2 * time}, ${"inner $msg"})")
    oneMore(time)
    log("after oneMore(${time})")
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
