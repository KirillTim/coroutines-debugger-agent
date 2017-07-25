package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

data class MethodInfo(
    val isAnonymous: Boolean = false,
    val isSuspend: Boolean = false,
    val isDoResume: Boolean = false,
    val isStateMachine: Boolean = false)

data class MethodId(
    val name: String,
    val owner: String,
    val desc: String,
    val info: MethodInfo? = null,
    val pretty: String = ""
) {
    override fun toString() = (if (pretty.isNotEmpty()) pretty else "$owner $name $desc") + "${info ?: ""}"
}

data class DoResumeForSuspend(val doResume: MethodId, val suspend: SuspendFunction)

data class FunctionCall(val function: MethodId, val file: String, val line: Int, val fromFunction: String? = null) {
    override fun toString() = "$function at $file;$line"
}

sealed class SuspendFunction(open val method: MethodId)

data class AnonymousSuspendFunction(override val method: MethodId) : SuspendFunction(method)

data class NamedSuspendFunction(override val method: MethodId) : SuspendFunction(method)

data class UnknownBodySuspendFunction(override val method: MethodId) : SuspendFunction(method)