package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

data class MethodId private constructor(val name: String, val owner: String, val desc: String) {
    override fun toString() = "$owner.$name $desc"
    fun equalsTo(ste: StackTraceElement) = name == ste.methodName && owner == ste.className

    companion object {
        fun build(name: String, owner: String, desc: String) = MethodId(name, owner.replace('/', '.'), desc)
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
        open val position: CallPosition,
        open val fromMethod: MethodId? = null) {
    abstract val stackTraceElement: StackTraceElement
    override fun toString() = "$method $position"
}

data class DoResumeCall(
        override val method: MethodId,
        override val position: CallPosition,
        override val fromMethod: MethodId? = null
) : MethodCall(method, position, fromMethod) {
    override val stackTraceElement
            by lazy { StackTraceElement(method.owner, "invoke", position.file, position.line) }

    override fun toString() = super.toString()
}

sealed class SuspendCall(
        override val method: MethodId,
        override val position: CallPosition,
        override val fromMethod: MethodId? = null
) : MethodCall(method, position, fromMethod) {
    override fun toString() = super.toString()
}

data class NamedFunctionSuspendCall(
        override val method: MethodId,
        override val position: CallPosition,
        override val fromMethod: MethodId? = null
) : SuspendCall(method, position, fromMethod) {
    override val stackTraceElement
            by lazy { StackTraceElement(method.owner, method.name, position.file, position.line) }

    override fun toString() = super.toString()
}

data class InvokeSuspendCall(
        override val method: MethodId,
        override val position: CallPosition,
        override val fromMethod: MethodId?,
        var realOwner: String? = null
) : SuspendCall(method, position, fromMethod) {
    override val stackTraceElement
            by lazy { StackTraceElement(realOwner ?: method.owner, method.name, position.file, position.line) }

    override fun toString() = super.toString()
}