package example

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import mylibrary.Printer

fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main(args: Array<String>) = runBlocking<Unit> {
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
}

/*
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking

fun main(args: Array<String>) = runBlocking<Unit> {
    println("Started!")
    test()
    /*test(1234)
    testInside()
    println("Done.")*/
}

suspend fun testInside() {
    test()
}

suspend fun test(time: Long) {
    println("test($time)")
    //delay(time)
}

suspend fun test() {
    println("test()")
    delay(1000)
}*/
