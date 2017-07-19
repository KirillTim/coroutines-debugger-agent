package mylibrary

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/**
 * @author Kirill Timofeev
 */

sealed class SuspendFunction {
    abstract fun prettyPrint(argumentValues: List<Any>? = null): String

    protected fun argumentsString(argumentTypes: Array<Type>, argumentValues: List<Any>?) =
            argumentValues?.joinToString() ?: argumentTypes.joinToString(transform = { it.className.split('.').last() })
}

data class LibrarySuspendFunction(val name: String, val owner: String, val desc: String) : SuspendFunction() {
    override fun prettyPrint(argumentValues: List<Any>?): String {
        val type = Type.getType(desc)
        val arguments = argumentsString(type.argumentTypes, argumentValues)
        val returnType = type.returnType.className.split('.').last()
        return "${owner.replace('/', '.')}.$name($arguments): $returnType"
    }
}

sealed class UserDefinedSuspendFunction(open val method: MethodNode, open val owner: ClassNode,
                                        open val lineNumber: Int) : SuspendFunction() {
    override fun prettyPrint(argumentValues: List<Any>?): String {
        val type = Type.getType(method.desc)
        val arguments = argumentsString(type.argumentTypes, argumentValues)
        val name = when (this) {
            is AnonymousSuspendFunction -> "anonymous"
            is NamedSuspendFunction -> "${this.owner.name.replace('/', '.')}.${this.method.name}"
        }
        val returnType = type.returnType.className.split('.').last()
        return "$name($arguments): $returnType, defined at ${owner.sourceFile}:$lineNumber"
    }
}

data class AnonymousSuspendFunction(override val method: MethodNode, override val owner: ClassNode,
                                    override val lineNumber: Int)
    : UserDefinedSuspendFunction(method, owner, lineNumber) {
    override fun toString() = "anonymous in ${owner.name} : ${method.desc}, defined at ${owner.sourceFile}:$lineNumber"
}

data class NamedSuspendFunction(override val method: MethodNode, override val owner: ClassNode,
                                override val lineNumber: Int)
    : UserDefinedSuspendFunction(method, owner, lineNumber) {
    override fun toString() = "${owner.name}.${method.name} : ${method.desc}, defined at ${owner.sourceFile}:$lineNumber"
}