package kotlinx.coroutines.debug.manager

import java.lang.reflect.Modifier

/**
 * @author Kirill Timofeev
 */

//debug only
fun Any?.objToString() = ObjectPrinter.objToString(this)

//debug only
fun Any.fullInfo() = ObjectPrinter.fullInfo(this)

//debug only
object ObjectPrinter { //may be called from user code for debug sometimes
    @JvmStatic
    fun objToString(obj: Any?) = try {
        "$obj"
    } catch (e: Exception) {
        "<can't be printed>"
    }

    @JvmStatic
    fun fullInfo(obj: Any) =
            buildString {
                append("fields of ${objToString(obj)} with type: ${obj.javaClass.name}\n")
                val fields = obj.javaClass.declaredFields
                for (f in fields) {
                    append(try {
                        f.isAccessible = true
                        val prefix = if (Modifier.isPrivate(f.modifiers)) "(private) " else ""
                        "$prefix ${f.name} : ${objToString(f.get(obj))}\n"
                    } catch (e: Exception) {
                        "can't fullInfo value of ${f.name}\n"
                    })
                }
                append("getters:\n")
                for (m in obj.javaClass.methods) {
                    if (m.name.startsWith("get") && m.genericParameterTypes.isEmpty()) {
                        m.isAccessible = true
                        append(try {
                            "${m.name} : ${objToString(m.invoke(obj))}\n"
                        } catch (e: Exception) {
                            "can't invoke ${m.name}\n"
                        })
                    }
                }
                append("---------------------------")
            }
}
