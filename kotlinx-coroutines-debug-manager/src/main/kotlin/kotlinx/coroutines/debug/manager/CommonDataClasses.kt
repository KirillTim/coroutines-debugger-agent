package kotlinx.coroutines.debug.manager

/**
 * @author Kirill Timofeev
 */

data class MethodId(val name: String, val className: String, val desc: String) {
    init { check(!className.contains('/')) { "Must be a Java class name, not an internal name"} }

    override fun toString() = "$className.$name $desc"
    fun equalsTo(ste: StackTraceElement) = name == ste.methodName && className == ste.className

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

sealed class MethodCall {
    abstract val method: MethodId
    abstract val fromMethod: MethodId
    abstract val position: CallPosition
    val stackTraceElement by lazy { StackTraceElement(fromMethod.className, fromMethod.name, position.file, position.line) }
    override fun toString() = "${method.name} from $fromMethod $position"
}

data class DoResumeCall(
    override val method: MethodId,
    override val fromMethod: MethodId,
    override val position: CallPosition
) : MethodCall() {
    override fun toString() = super.toString()
}

sealed class SuspendCall : MethodCall()

// todo: merge into SuspencCall class
data class NamedFunctionSuspendCall(
    override val method: MethodId,
    override val fromMethod: MethodId,
    override val position: CallPosition
) : SuspendCall() {
    override fun toString() = super.toString()
}

// todo: remove
data class InvokeSuspendCall(
    override val method: MethodId,
    override val fromMethod: MethodId,
    override val position: CallPosition,
    var realOwner: String? = null // todo: remove unused,
) : SuspendCall() {
    override fun toString() = super.toString()
}