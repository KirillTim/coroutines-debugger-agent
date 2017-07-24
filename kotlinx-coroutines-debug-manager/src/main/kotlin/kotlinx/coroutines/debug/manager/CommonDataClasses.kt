package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

data class MethodInfo(val isAnonymous: Boolean = false, val isSuspend: Boolean = false,
                      val isDoResume: Boolean = false, val isStateMachine: Boolean = false)

sealed class MethodId(open val name: String, open val owner: String, open val desc: String) {
    override fun toString() = "$owner $name $desc"
}

data class MethodIdSimple(override val name: String, override val owner: String, override val desc: String)
    : MethodId(name, owner, desc)

data class MethodIdWithInfo(override val name: String, override val owner: String, override val desc: String,
                            val info: MethodInfo = MethodInfo(), val pretty: String = "") : MethodId(name, owner, desc) {
    override fun toString() = (if (pretty.isNotEmpty()) pretty else "$owner $name $desc") + "$info"
}

data class DoResumeForSuspend(val doResume: MethodId, val suspend: SuspendFunction)
    : MethodId(doResume.name, doResume.owner, doResume.desc)

data class FunctionCall(val function: MethodId, val file: String, val line: Int, val fromFunction: String? = null) {
    override fun toString() = "$function at $file;$line"
}

sealed class SuspendFunction(open val method: MethodId) : MethodId(method.name, method.owner, method.desc)

data class AnonymousSuspendFunction(override val method: MethodId) : SuspendFunction(method)

data class NamedSuspendFunction(override val method: MethodId) : SuspendFunction(method)

data class UnknownBodySuspendFunction(override val method: MethodId) : SuspendFunction(method)