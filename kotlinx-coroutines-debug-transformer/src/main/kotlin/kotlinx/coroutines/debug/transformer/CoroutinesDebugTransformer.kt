package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.*
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.tree.ClassNode
import org.jetbrains.org.objectweb.asm.tree.MethodNode
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

private fun SuspendCallInsnNode.buildSuspendCall(
        classNode: ClassNode,
        position: CallPosition,
        calledFrom: MethodNode
): SuspendCall = when (this) {
    is NamedSuspendCallInsnNode ->
        NamedFunctionSuspendCall(insn.buildMethodId(), calledFrom.buildMethodId(classNode), position)
    is InvokeSuspendCallInsnNode ->
        InvokeSuspendCall(insn.buildMethodId(), calledFrom.buildMethodId(classNode), position)
}

private fun MethodNode.addSuspendCallHandlers(
        suspendCallsInThisMethod: List<SuspendCallInsnNode>,
        continuationVarIndex: Int, classNode: ClassNode
) {
    val lines = instructions.methodCallLineNumber()
    suspendCallsInThisMethod.forEach {
        val position = CallPosition(classNode.sourceFile, lines[it.insn] ?: -1)
        allSuspendCalls += it.buildSuspendCall(classNode, position, this)
        val (index, call) = allSuspendCalls.withIndex().last()
        when (call) {
            is NamedFunctionSuspendCall ->
                instructions.insert(it.insn, generateAfterNamedSuspendCall(continuationVarIndex, index))
            is InvokeSuspendCall -> {
                val (before, after) = generateInvokeSuspendCallHandler(this, it.insn, continuationVarIndex, index)
                instructions.insertBefore(it.insn, before)
                instructions.insert(it.insn, after)
            }
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
        knownDoResumeFunctions += buildMethodId(classNode)
        instructions.insert(generateHandleDoResumeCallEnter(continuation, knownDoResumeFunctions.lastIndex))
        if (!isAnonymous) return
    }
    val suspendCalls = suspendCallInstructions(classNode)
    if (suspendCalls.isEmpty()) return
    debug {
        buildString { append("suspend calls:\n"); suspendCalls.forEach { append("${it.insn.buildMethodId()}\n") } }
    }
    addSuspendCallHandlers(suspendCalls, continuation, classNode)
}

class CoroutinesDebugTransformer : ClassFileTransformer {
    override fun transform(
            loader: ClassLoader?,
            className: String?,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray?
    ): ByteArray {
        if (className?.startsWith(DEBUG_AGENT_PACKAGE_PREFIX) == true && classfileBuffer != null) return classfileBuffer
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)
        for (method in classNode.methods.map { it as MethodNode }) {
            try {
                method.transformMethod(classNode)
            } catch (e: Exception) {
                exceptions.add(e)
                val message = "while instrumenting $className.${method.name} with desc: ${method.desc} " +
                        "exception : ${e.stackTraceToString()}"
                error { message }
                debug { message + "\nbytecode: ${classNode.byteCodeString()}" }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}