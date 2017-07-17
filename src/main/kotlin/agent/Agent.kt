package agent

import mylibrary.*
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

private fun getCoroutineContextVarIndexForSuspendFunction(method: MethodNode) =
        getCoroutineContext(method)?.localVar?.index
                ?: throw IllegalArgumentException("Can't find coroutine context in suspend function ${method.name}")


private fun generateAfterSuspendCall(suspendCall: MethodInsnNode, ctxVarIndex: Int, calledFrom: String): InsnList {
    val list = InsnList()
    list.add(InsnNode(Opcodes.DUP))
    list.add(IntInsnNode(Opcodes.ALOAD, ctxVarIndex))
    list.add(LdcInsnNode(suspendCall.name))
    list.add(LdcInsnNode(suspendCall.desc)) //FIXME can find function by this three parameters
    list.add(LdcInsnNode(suspendCall.owner))
    list.add(LdcInsnNode(calledFrom))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutineStack", "afterSuspendCall",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false))
    return list
}

//each suspend function can be determined by doResume owner class
private fun generateHandleOnResumeCall(ctxVarIndex: Int, owner: String): InsnList {
    val list = InsnList()
    list.add(IntInsnNode(Opcodes.ALOAD, ctxVarIndex))
    list.add(LdcInsnNode(owner))
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutineStack", "handleOnResume",
            "(Ljava/lang/Object;Ljava/lang/String;)V", false))
    return list
}

private fun addSuspendCallHandlers(method: MethodNode, classNode: ClassNode) {
    //println(">>in method ${classNode.name}.${method.name}")
    val ctxIndex = getCoroutineContextVarIndexForSuspendFunction(method)
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

private fun transformMethod(method: MethodNode, classNode: ClassNode) {
    val isStateMachine = isStateMachineForAnonymousSuspendFunction(method)
    if (method.isSuspend() || isStateMachine) {
        if (method.name == "invoke") {
            //insertPrintln("invoke call for ${classNode.name}", method.instructions)
        } else {
            addSuspendCallHandlers(method, classNode)
        }
    }
    /*if (method.name == "<init>") {
        insertPrintln("created new ${classNode.name} object", method.instructions)
    }*/
    if (method.name == "doResume") {
        if (!isStateMachine) {
            val function = correspondingSuspendFunctionForDoResume(method)
            val ctxVarIndex = getCoroutineContextVarIndexForSuspendFunction(method)
            method.instructions.insert(generateHandleOnResumeCall(ctxVarIndex, function.owner))
            //insertPrintln("doResume call for function $function", method.instructions)
        }
    }
}

class TestTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)
        if (classNode.name.contains("example")) { //FIXME
            typesInfo[classNode.name] = ExtendsImplements(classNode.superName, classNode.interfaces.map { it as String })
            //println(">>in class ${classNode.name}")
            for (method in classNode.methods.map { it as MethodNode }) {
                try {
                    val prevSize = doResumeToSuspendFunction.size
                    updateDoResumeToSuspendFunctionMap(method, classNode)
                    if (doResumeToSuspendFunction.size != prevSize) {
                        /*println("\ndoResume to suspend functions:")
                        for ((doResume, suspendFunc) in doResumeToSuspendFunction) {
                            println("${doResume.classNode.name}${doResume.method.name} -> $suspendFunc")
                        }
                        println()*/
                    }
                    transformMethod(method, classNode)
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

