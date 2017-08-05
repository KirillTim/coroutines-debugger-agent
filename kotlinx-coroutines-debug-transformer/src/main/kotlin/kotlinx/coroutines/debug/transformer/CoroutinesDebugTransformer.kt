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

private fun ClassNode.isCoroutineImplOrSubType()  //FIXME should we really check the type?
        = name == COROUTINE_IMPL_TYPE.internalName || superName == COROUTINE_IMPL_TYPE.internalName

private fun argumentVarIndex(argumentTypes: Array<Type>, argumentIndex: Int)
        = argumentTypes.take(argumentIndex).map { it.size }.sum()

private fun MethodNode.findContinuationVarIndex(classNode: ClassNode): Int {
    if (classNode.isCoroutineImplOrSubType()) return 0 //index of `this` variable
    require(isSuspend(), { "Method should be suspend, got $desc instead" })
    val continuationArgIndex = Type.getType(desc).argumentTypes.size - continuationOffsetFromEndInDesc(name)
    val isStatic = access and Opcodes.ACC_STATIC != 0
    return argumentVarIndex(Type.getArgumentTypes(desc), continuationArgIndex) + if (isStatic) 0 else 1
}

private fun MethodNode.addSuspendCallHandlers(continuationVarIndex: Int, classNode: ClassNode) {
    val lines = methodCallLineNumber(instructions)
    for (i in instructions) {
        if (i is MethodInsnNode && i.isSuspend()) {
            Logger.default.debug {
                "instrument call ${i.owner}.${i.name}(${i.desc}) " +
                        "from ${classNode.name}.${name} at ${classNode.sourceFile}:${lines[i]}, " +
                        "cont index = $continuationVarIndex"
            }
            suspendCalls += FunctionCall(i.buildMethodId(),
                    CallPosition(classNode.sourceFile, lines[i] ?: -1),
                    buildMethodId(classNode))
            instructions.insert(i, generateAfterSuspendCall(continuationVarIndex, suspendCalls.lastIndex))
        }
    }
}

private fun MethodNode.addDoResumeCallExitHandler(continuationVarIndex: Int, doResumeIndex: Int) { //FIXME: make more robust
    val areturn = instructions.lastARETURN()
            ?: throw IllegalArgumentException("DoResume call should have at least one ARETURN instruction")
    instructions.insert(areturn.previous, generateHandleDoResumeCallExit(continuationVarIndex, doResumeIndex))
}

private fun MethodNode.transformMethod(classNode: ClassNode) {
    val isStateMachine = isStateMachineForAnonymousSuspendFunction()
    val isDoResume = isDoResume()
    val isSuspend = isSuspend()
    if (!isSuspend && !isStateMachine && !isDoResume) return
    val continuation = findContinuationVarIndex(classNode)
    //Logger.default.debug { ">>in method ${classNode.name}.${name} with description: ${desc}" }
    if (isSuspend || isStateMachine) {
        addSuspendCallHandlers(continuation, classNode)
    }
    if (isDoResume) {
        val methodId = buildMethodIdWithInfo(classNode)
        val forFunction = if (isStateMachine)
            AnonymousSuspendFunction(MethodId(name, classNode.name, desc)) else
            correspondingSuspendFunctionForDoResume()
        val doResumeFirstInsnPosition = CallPosition(classNode.sourceFile, firstInstructionLineNumber())
                .takeIf { isStateMachine }
        doResumeToSuspendFunctions += DoResumeForSuspend(methodId, forFunction, doResumeFirstInsnPosition)
        instructions.insert(generateHandleDoResumeCallEnter(continuation, doResumeToSuspendFunctions.lastIndex))
        if (isStateMachine) { //FIXME for now assume that only anonymous doResume can be entry point
            addDoResumeCallExitHandler(continuation, doResumeToSuspendFunctions.lastIndex)
        }
    }
}

class CoroutinesDebugTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)

        for (method in classNode.methods.map { it as MethodNode }) {
            try {
                method.transformMethod(classNode)
            } catch (e: Exception) {
                val message = "while instrumenting $className.${method.name} with desc: ${method.desc} " +
                        "exception : ${e.stackTraceToString()}"
                Logger.default.error { message }
                Logger.default.debug { message + "\nbyte code: ${classNode.byteCodeString()}" }
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}