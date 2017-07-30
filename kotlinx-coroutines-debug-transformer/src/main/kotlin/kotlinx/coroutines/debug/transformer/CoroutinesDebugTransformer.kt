package kotlinx.coroutines.debug.transformer

import kotlinx.coroutines.debug.manager.AnonymousSuspendFunction
import kotlinx.coroutines.debug.manager.DoResumeForSuspend
import kotlinx.coroutines.debug.manager.MethodId
import kotlinx.coroutines.debug.manager.doResumeToSuspendFunctions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.TraceClassVisitor
import java.io.PrintWriter
import java.io.StringWriter
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
            //println("instrument call ${i.owner}.${i.name}(${i.desc}) " +
            //        "from ${classNode.name}.${name} at ${classNode.sourceFile}:${lines[i]}, " +
            //        "cont index = $continuationVarIndex")
            instructions.insert(i, generateAfterSuspendCall(i, continuationVarIndex, name, classNode.sourceFile, lines[i] ?: -1))
        }
    }
}

private fun MethodNode.transformMethod(classNode: ClassNode) {
    val isStateMachine = isStateMachineForAnonymousSuspendFunction()
    val isDoResume = isDoResume()
    val isSuspend = isSuspend()
    if (!isSuspend && !isStateMachine && !isDoResume) return
    val continuation = findContinuationVarIndex(classNode)
    //println(">>in method ${classNode.name}.${name} with description: ${desc}")
    if (isSuspend || isStateMachine) {
        addSuspendCallHandlers(continuation, classNode)
    }
    if (isDoResume) {
        val methodId = buildMethodIdWithInfo(this, classNode)
        val forFunction = if (isStateMachine)
            AnonymousSuspendFunction(MethodId(name, classNode.name, desc)) else
            correspondingSuspendFunctionForDoResume()
        doResumeToSuspendFunctions += DoResumeForSuspend(methodId, forFunction)
        instructions.insert(generateHandleDoResumeCall(continuation, doResumeToSuspendFunctions.lastIndex))
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
                val trace = StringWriter()
                e.printStackTrace(PrintWriter(trace))
                val byteCodeStr = StringWriter()
                classNode.accept(TraceClassVisitor(PrintWriter(byteCodeStr)))
                println("while instrumenting $className.${method.name} with desc: ${method.desc} exception : $trace" +
                        "\n byte code: $byteCodeStr")
            }
        }
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}