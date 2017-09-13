package kotlinx.coroutines.debug.manager

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Modifier

/**
 * @author Kirill Timofeev
 */

fun Any?.toStringSafe() = try {
    toString()
} catch (e: Throwable) {
    "<can't be printed ($e)>"
}

fun Throwable.stackTraceToString(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString()
}

//debug only
fun Any.fullInfo() = ObjectPrinter.fullInfo(this)

//debug only
object ObjectPrinter { //may be called from user code for debug sometimes
    @JvmStatic
    fun fullInfo(obj: Any) =
            buildString {
                append("fields of ${obj.toStringSafe()} with type: ${obj.javaClass.name}\n")
                val fields = obj.javaClass.declaredFields
                for (f in fields) {
                    append(try {
                        f.isAccessible = true
                        val prefix = if (Modifier.isPrivate(f.modifiers)) "(private) " else ""
                        "$prefix ${f.name} : ${f.get(obj).toStringSafe()}\n"
                    } catch (e: Exception) {
                        "can't fullInfo value of ${f.name}\n"
                    })
                }
                append("getters:\n")
                for (m in obj.javaClass.methods) {
                    if (m.name.startsWith("get") && m.genericParameterTypes.isEmpty()) {
                        m.isAccessible = true
                        append(try {
                            "${m.name} : ${m.invoke(obj).toStringSafe()}\n"
                        } catch (e: Exception) {
                            "can't invoke ${m.name}\n"
                        })
                    }
                }
                append("---------------------------")
            }
}
