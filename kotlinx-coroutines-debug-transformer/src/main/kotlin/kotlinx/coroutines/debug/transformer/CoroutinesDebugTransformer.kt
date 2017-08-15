package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain

/**
 * @author Kirill Timofeev
 */

private fun ClassNode.isCoroutineImplOrSubType() =
        name == COROUTINE_IMPL_TYPE.internalName || superName == COROUTINE_IMPL_TYPE.internalName

private fun argumentVarIndex(argumentTypes: Array<Type>, argumentIndex: Int) =
        argumentTypes.take(argumentIndex).map { it.size }.sum()

private fun MethodNode.findContinuationVarIndex(classNode: ClassNode): Int {
    if (classNode.isCoroutineImplOrSubType()) return 0 //index of `this`
    require(isSuspend(), { "Method should be suspend, got $desc instead" })
    val continuationArgIndex = Type.getType(desc).argumentTypes.size - continuationOffsetFromEndInDesc(name)
    val isStatic = access and Opcodes.ACC_STATIC != 0
    return argumentVarIndex(Type.getArgumentTypes(desc), continuationArgIndex) + if (isStatic) 0 else 1
}

private fun MethodNode.addSuspendCallHandlers(continuationVarIndex: Int, classNode: ClassNode) {
    val lines = instructions.methodCallLineNumber()
    for (i in instructions) {
        if (i is MethodInsnNode && i.isSuspend()) {
            /*debug {
                "instrument call ${i.owner}.${i.event}(${i.desc}) " +
                        "from ${classNode.event}.${event} at ${classNode.sourceFile}:${lines[i]}, " +
                        "cont index = $continuationVarIndex"
            }*/
            suspendCalls += MethodCall(i.buildMethodId(),
                    CallPosition(classNode.sourceFile, lines[i] ?: -1),
                    buildMethodId(classNode))
            instructions.insert(i, generateAfterSuspendCall(continuationVarIndex, suspendCalls.lastIndex))
        }
    }
}

private fun MethodNode.transformCreate() {
    val completion = Type.getType(desc).argumentTypes.lastIndex + 1
    debug { "create type: $desc, completion: $completion" }
    var newInsn = instructions.first
    while (newInsn.opcode != Opcodes.NEW)
        newInsn = newInsn?.nextMeaningful
    instructions.insertBefore(newInsn, generateNewWrappedCompletion(completion))
}

private fun MethodNode.transformMethod(classNode: ClassNode) {
    if (isCreateCoroutine(classNode)) {
        transformCreate()
        return
    }
    if (!isSuspend() && !isDoResume) return
    val continuation = findContinuationVarIndex(classNode)
    val isAnonymous = isStateMachineForAnonymousSuspendFunction()
    debug {
        ">>in method ${classNode.name}.${name} with description: ${desc}, " +
                "cont: $continuation, isAnonymous: $isAnonymous"
    }
    if (isDoResume) {
        val methodId = buildMethodIdWithInfo(classNode)
        val forFunction = if (isAnonymous)
            AnonymousSuspendFunction(MethodId.build(name, classNode.name, desc)) else
            correspondingSuspendFunctionForDoResume()
        val doResumeFirstInsnPosition = CallPosition(classNode.sourceFile, firstInstructionLineNumber())
                .takeIf { isAnonymous }
        doResumeToSuspendFunctions += DoResumeForSuspend(methodId, forFunction, doResumeFirstInsnPosition)
        instructions.insert(generateHandleDoResumeCallEnter(continuation, doResumeToSuspendFunctions.lastIndex))
        if (!isAnonymous) return
    }
    addSuspendCallHandlers(continuation, classNode)
}

class CoroutinesDebugTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        if (className?.startsWith(DEBUG_AGENT_PACKAGE_PREFIX) == true && classfileBuffer != null) return classfileBuffer
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)
        for (method in classNode.methods.map { it as MethodNode }) {
            try {
                method.transformMethod(classNode)
            } catch (e: Exception) {
                val message = "while instrumenting $className.${method.name} with desc: ${method.desc} " +
                        "exception : ${e.stackTraceToString()}"
                error { message }
                debug { message + "\nbyte code: ${classNode.byteCodeString()}" }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}