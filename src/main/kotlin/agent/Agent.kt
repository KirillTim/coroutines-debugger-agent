package agent

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl

class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(TestTransformer())
        }
    }
}

private sealed class WithContextVar(val localVar: LocalVariableNode)
private class CoroutineImplVar(localVar: LocalVariableNode) : WithContextVar(localVar)
private class ContinuationVar(localVar: LocalVariableNode) : WithContextVar(localVar)

private fun isCoroutineImplOrSubType(variable: LocalVariableNode): Boolean { //FIXME
    val coroutineImplType = Type.getType(CoroutineImpl::class.java)
    if (Type.getType(variable.desc) == coroutineImplType) {
        return true
    }
    val extends = typesInfo[Type.getType(variable.desc).internalName]?.extends ?: return false
    return extends == coroutineImplType.internalName
}

private fun getCoroutineContext(method: MethodNode): WithContextVar? {
    val locals = method.localVariables?.map { it as LocalVariableNode } ?: return null
    val thisVar = locals.firstOrNull { it.name == "this" }
    if (thisVar != null && isCoroutineImplOrSubType(thisVar)) {
        return CoroutineImplVar(thisVar)
    }
    return ContinuationVar(locals[continuationVarIndex(method) ?: return null])
}

private fun generateAfterSuspendCall(suspendCall: MethodInsnNode, ctxVarIndex: Int, calledFrom: String): InsnList {
    val list = InsnList()
    list.add(InsnNode(Opcodes.DUP))
    list.add(IntInsnNode(Opcodes.ALOAD, ctxVarIndex))
    list.add(LdcInsnNode(suspendCall.name))
    list.add(LdcInsnNode(calledFrom))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutineStack", "afterSuspendCall",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false))
    return list
}

private fun addSuspendCallHandlers(method: MethodNode, classNode: ClassNode) {
    println(">>in method ${classNode.name}.${method.name}")
    val ctx = getCoroutineContext(method)
    val ctxIndex = when (ctx) {
        is WithContextVar -> ctx.localVar.index
        else -> throw IllegalArgumentException("Can't find coroutine context in suspend function ${method.name}")
    }
    val iter = method.instructions.iterator()
    while (iter.hasNext()) {
        val i = iter.next()
        if (i is MethodInsnNode && i.isSuspend()) {
            println("instrument call ${i.owner}.${i.name} from ${classNode.name}.${method.name}")
            method.instructions.insert(i, generateAfterSuspendCall(i, ctxIndex, "${classNode.name}.${method.name}"))
        }
    }
}


private data class ExtendsImplements(val extends: String?, val implements: List<String>)

private val typesInfo = mutableMapOf<String, ExtendsImplements>() //FIXME

class TestTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)
        if (classNode.name.contains("example")) { //FIXME
            if (!typesInfo.containsKey(classNode.name)) {
                typesInfo[classNode.name] = ExtendsImplements(classNode.superName, classNode.interfaces.map { it as String })
            }
            println(">> in class ${classNode.name}")
            for (method in classNode.methods.map { it as MethodNode }) {
                try {
                    if (method.isSuspend() || isStateMachineForAnonymousSuspendFunction(method)) {
                        addSuspendCallHandlers(method, classNode)
                    }
                    if (method.name == "doResume") {
                        method.instructions.insert(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/ObjectPrinter", "printText",
                                "(Ljava/lang/String;)V", false))
                        method.instructions.insert(LdcInsnNode("doResume call for class $className"))
                    }
                    //println(">>> doResume for $className")

                    /* val labels = method.instructions.toArray().filterIsInstance<LineNumberNode>()
                             .map { it.start.label!! to it.line }.toMap()
                     val methodCalls = methodCallNodeToLabelNode(method.instructions)
                     if (isStateMachineForAnonymousSuspendFunction(method.instructions)) {
                         println("anonymous suspend lambda in ${classNode.name}")
                         for ((m, label) in methodCalls.filterKeys { it.isSuspend() }) {
                             println("${labels[label.label]} -> ${m.name} : ${m.desc}")
                         }
                     }
                 }*/
                } catch (e: Exception) {
                    println("exception : $e")
                }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}

