package agent

import mylibrary.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.PrintWriter
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl
import java.io.StringWriter


class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            SocketWriter.start(8889, stacks)
            inst.addTransformer(AddCoroutinesInfoTransformer())
        }
    }
}

private sealed class WithContextVarIndex(open val index: Int)
private data class CoroutineImplVar(override val index: Int) : WithContextVarIndex(index)
private data class ContinuationVar(override val index: Int) : WithContextVarIndex(index)

private fun isCoroutineImplOrSubType(variable: LocalVariableNode): Boolean { //FIXME
    val coroutineImplType = Type.getType(CoroutineImpl::class.java)
    if (Type.getType(variable.desc) == coroutineImplType) {
        return true
    }
    val extends = typesInfo[Type.getType(variable.desc).internalName]?.extends ?: return false
    return extends == coroutineImplType.internalName
}

private fun argumentVarIndex(argumentTypes: Array<Type>, argumentIndex: Int)
        = argumentTypes.take(argumentIndex).map { it.size }.sum()

private fun getContinuationVarIndex(method: MethodNode): WithContextVarIndex {
    //FIXME use getfield instruction instead
    val thisVar = method.localVariables?.map { it as LocalVariableNode }?.firstOrNull { it.name == "this" }
    if (thisVar != null && isCoroutineImplOrSubType(thisVar)) {
        return CoroutineImplVar(thisVar.index)
    }
    val continuationIndex = continuationArgumentIndex(method)
            ?: throw IllegalArgumentException("Can't find Continuation in ${method.desc}")
    return ContinuationVar(argumentVarIndex(Type.getArgumentTypes(method.desc), continuationIndex))
}

private data class ExtendsImplements(val extends: String?, val implements: List<String>)

private val typesInfo = mutableMapOf<String, ExtendsImplements>() //FIXME

private fun addSuspendCallHandlers(continuationVarIndex: Int, method: MethodNode, classNode: ClassNode) {
    println(">>in method ${classNode.name}.${method.name} with description: ${method.desc}")
    println("continuation var index: $continuationVarIndex")
    val iter = method.instructions.iterator()
    while (iter.hasNext()) {
        val i = iter.next()
        if (i is MethodInsnNode && i.isSuspend()) {
            println("instrument call ${i.owner}.${i.name} from ${classNode.name}.${method.name}")
            method.instructions.insert(i, generateAfterSuspendCall(i, continuationVarIndex, "${classNode.name}.${method.name}"))
        }
    }
}

private fun transformMethod(method: MethodNode, classNode: ClassNode) {
    val isStateMachine = isStateMachineForAnonymousSuspendFunction(method)
    val isDoResume = method.isDoResume()
    val isSuspend = method.isSuspend()
    if (isSuspend || isStateMachine || isDoResume) {
        val continuation = getContinuationVarIndex(method)
        println(">>in method ${classNode.name}.${method.name} with description: ${method.desc}, cont: $continuation")
        if (isSuspend || isStateMachine) {
            addSuspendCallHandlers(continuation.index, method, classNode)
        }
        if (isDoResume) {
            val function = if (isStateMachine)
                MethodNameOwnerDesc(method.name, classNode.name, method.desc)
            else
                correspondingSuspendFunctionForDoResume(method)
            method.instructions.insert(generateHandleDoResumeCall(continuation.index, function))
        }
    }
}

class AddCoroutinesInfoTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)

        typesInfo[classNode.name] = ExtendsImplements(classNode.superName, classNode.interfaces.map { it as String })
        //println(">>in class ${classNode.name}")
        for (method in classNode.methods.map { it as MethodNode }) {
            try {
                updateDoResumeToSuspendFunctionMap(method, classNode)
                transformMethod(method, classNode)
            } catch (e: Exception) {
                val trace = StringWriter()
                e.printStackTrace(PrintWriter(trace));
                println("while instrumenting $className.${method.name} with desc: ${method.desc} exception : $trace")
            }
        }

        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}

