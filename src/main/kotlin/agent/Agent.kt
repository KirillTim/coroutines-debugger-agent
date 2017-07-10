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

sealed class WithContextVar(val localVar: LocalVariableNode)
class CoroutineImplVar(localVar: LocalVariableNode) : WithContextVar(localVar)
class ContinuationVar(localVar: LocalVariableNode) : WithContextVar(localVar)

fun getCoroutineContext(method: MethodNode): WithContextVar? {
    val locals = method.localVariables?.map { it as LocalVariableNode } ?: return null
    val thisVar = locals.firstOrNull { it.name == "this" }
    if (thisVar != null && Type.getType(thisVar.desc) == Type.getType(CoroutineImpl::class.java)) {
        return CoroutineImplVar(thisVar)
    }
    return ContinuationVar(locals.get(continuationVarIndex(method) ?: return null))
}


fun addSuspendCallHandlers(method: MethodNode, classNode: ClassNode) {
    println(">>in method ${classNode.name}.${method.name}")
    val ctx = getCoroutineContext(method)
    val ctxIndex = when (ctx) {
        is WithContextVar -> {
            println("context var with index ${ctx.localVar.index}")
            ctx.localVar.index
        }
        else -> throw IllegalArgumentException("Can't find coroutine context in suspend function")
    }

    val iter = method.instructions.iterator()
    while (iter.hasNext()) {
        val i = iter.next()
        if (i is MethodInsnNode && i.isSuspend()) {
            println("instrument call ${i.owner}.${i.name} from ${classNode.name}.${method.name}")
            val after = InsnList()
            after.add(InsnNode(Opcodes.DUP))
            after.add(IntInsnNode(Opcodes.ALOAD, ctxIndex))
            after.add(LdcInsnNode("${i.name}"))
            after.add(LdcInsnNode("${classNode.name}.${method.name}"))
            after.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutineStack", "afterSuspendCall",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V"
                    , false))
            method.instructions.insert(i, after)
            /*val before = InsnList()
            before.add(IntInsnNode(Opcodes.ALOAD, thisVar.index))
            before.add(LdcInsnNode("${i.name}"))
            before.add(LdcInsnNode("${classNode.name}.${method.name}"))
            before.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/CoroutineStack", "beforeSuspendCall",
                    "(Lkotlin/coroutines/experimental/jvm/internal/CoroutineImpl;Ljava/lang/String;Ljava/lang/String;)V", false))
            method.instructions.insertBefore(i, before)*/
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
            println("classNode.name: ${classNode.name}")
            for (method in classNode.methods.map { it as MethodNode }) {
                //println(">>>>>>> now in ${classNode.name}.${method.name}")
                try {
                    if (method.name == "doResume") {
                        method.instructions.insert(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/ObjectPrinter", "printText",
                                "(Ljava/lang/String;)V", false))
                        method.instructions.insert(LdcInsnNode("doResume call for class $className"))
                        //println(">>> doResume for $className")

                        /*val labels = method.instructions.toArray().filterIsInstance<LineNumberNode>()
                                .map { it.start.label!! to it.line }.toMap()
                        val methodCalls = methodCallNodeToLabelNode(method.instructions)
                        if (isAnonymousSuspendFunction(method.instructions)) {
                            println("anonymous suspend lambda in ${classNode.name}")
                            for ((m, label) in methodCalls.filterKeys { it.isSuspend() }) {
                                println("${labels[label.label]} -> ${m.name} : ${m.desc}")
                            }
                        }*/
                    }
                    if (method.isSuspend()) {
                        addSuspendCallHandlers(method, classNode)
                    }
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

