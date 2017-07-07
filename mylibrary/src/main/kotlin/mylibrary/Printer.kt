package mylibrary
/**
 * @author Kirill Timofeev
 */
class Printer {
    fun print() {
        println("print from library")
    }
}

data class Tmp(val name: String, val ids: List<Int>)

fun main(args: Array<String>) {
    val tmp = Tmp("Tmp", listOf(1,2,3))
    ObjectPrinter.print(tmp)
}