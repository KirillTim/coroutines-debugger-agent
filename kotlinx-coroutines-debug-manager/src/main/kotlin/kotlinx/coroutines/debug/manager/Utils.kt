package kotlinx.coroutines.debug.manager

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * @author Kirill Timofeev
 */

object ObjectPrinter {
    @JvmStatic
    fun objToString(obj: Any): String {
        var objStr = "<can't be printed>"
        try {
            objStr = "$obj"
        } catch (ignored: Exception) {
        }
        return objStr
    }

    @JvmStatic
    fun print(obj: Any) {
        println("print fields of " + objToString(obj) + " with type: " + obj.javaClass.name)
        val fields = obj.javaClass.declaredFields
        for (f in fields) {
            try {
                f.isAccessible = true
                val prefix = if (Modifier.isPrivate(f.modifiers)) "(private) " else ""
                println(prefix + f.name + " : " + objToString(f.get(obj)))
            } catch (e: IllegalAccessException) {
                println("while printing value of " + f.name + "exception: " + e.message)
                //e.printStackTrace();
            }

        }
        println("print getters:")
        for (m in obj.javaClass.methods) {
            if (m.name.startsWith("get") && m.genericParameterTypes.isEmpty()) {
                m.isAccessible = true
                try {
                    println(m.name + ": " + objToString(m.invoke(obj)))
                } catch (e: Exception) {
                    println("while invoking " + m.name + "exception: " + e.message)
                }
            }
        }
        println("---------------------------")
    }
}
