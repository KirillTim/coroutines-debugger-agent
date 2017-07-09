package agent

import mylibrary.ObjectPrinter
import org.objectweb.asm.*
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.LocalVariablesSorter
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.*
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.jvm.internal.CoroutineImpl
import java.io.PrintStream


class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(TestTransformer())
        }
    }
}

class TestTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val reader = ClassReader(classfileBuffer)
        val classNode = ClassNode()
        reader.accept(classNode, 0)
        if (classNode.name.contains("example") && classNode.superName.contains("CoroutineImpl")) { //FIXME
            for (method in classNode.methods.map { it as MethodNode }) {
                if (method.name == "doResume") {
                    println("doResume found in ${classNode.name}")
                    val list = InsnList()
                    list.add(LdcInsnNode("inside doResume!!!11"))
                    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/ObjectPrinter", "printText", "(Ljava/lang/String;)V", false))
                    method.instructions.insert(list)
                }

                val iter = method.instructions.iterator()
                while (iter.hasNext()) {
                    val i = iter.next()
                    when (i) {
                        is LineNumberNode -> {
                            //println("line number: ${i.line}, ${i.start.label}")
                        }
                        is LabelNode -> {
                            //println("label: ${i.label}")
                        }
                        is MethodInsnNode -> {
                            if (i.name != "<init>" && i.desc.contains("Continuation")) { //FIXME
                                println("${i.name} : ${i.desc}")
                                val after = InsnList()
                                after.add(LdcInsnNode("just after suspend"))
                                after.add(MethodInsnNode(Opcodes.INVOKESTATIC, "mylibrary/ObjectPrinter", "printText", "(Ljava/lang/String;)V", false))
                                method.instructions.insert(i, after)
                            }
                        }
                        else -> {
                        }
                    }
                }
            }
        }
        val writer = ClassWriter(0)
        classNode.accept(writer)
        return writer.toByteArray()
    }
}

fun printVar(varIndex: Int): InsnList {
    val list = InsnList()
    list.add(IntInsnNode(Opcodes.ILOAD, varIndex))
    val owner = Type.getType(ObjectPrinter::class.java).internalName
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "print", "(Ljava/lang/Object;)V", false))
    return list
}
