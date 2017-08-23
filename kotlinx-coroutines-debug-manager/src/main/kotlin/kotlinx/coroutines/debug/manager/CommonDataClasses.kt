package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

data class MethodId private constructor(val name: String, val owner: String, val desc: String) {
    override fun toString() = "$owner.$name $desc"
    fun equalsTo(ste: StackTraceElement) = name == ste.methodName && owner == ste.className

    companion object {
        fun build(name: String, owner: String, desc: String) = MethodId(name, owner.replace('/', '.'), desc)
        val UNKNOWN = MethodId("unknown", "unknown", "unknown")
    }
}

data class CallPosition(val file: String, val line: Int) {
    override fun toString() = "at $file:$line"

    companion object {
        val UNKNOWN = CallPosition("unknown", -1)
    }
}

sealed class MethodCall(
        open val method: MethodId,
        open val fromMethod: MethodId,
        open val position: CallPosition) {
    val stackTraceElement by lazy { StackTraceElement(fromMethod.owner, fromMethod.name, position.file, position.line) }
    override fun toString() = "${method.name} from $fromMethod $position"
}

data class DoResumeCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition
) : MethodCall(method, fromMethod, position) {
    override fun toString() = super.toString()
}

sealed class SuspendCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition
) : MethodCall(method, fromMethod, position) {
    override fun toString() = super.toString()
}

data class NamedFunctionSuspendCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition
) : SuspendCall(method, fromMethod, position) {
    override fun toString() = super.toString()
}

data class InvokeSuspendCall(
        override val method: MethodId,
        override val fromMethod: MethodId,
        override val position: CallPosition,
        var realOwner: String? = null
) : SuspendCall(method, fromMethod, position) {
    override fun toString() = super.toString()
}