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

class PrintContinuationNameAdapter(val access: Int, val method: Method, mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {

    val lvs = LocalVariablesSorter(access, method.descriptor, mv)
    val generator = GeneratorAdapter(access, method, mv)

    override fun visitCode() {
        generator.getStatic(Type.getType(System::class.java), "out", Type.getType(PrintStream::class.java))
        generator.push("inside ${method.name}")
        generator.invokeVirtual(Type.getType(PrintStream::class.java),
                Method.getMethod("void println (String)"))

    }
}

class TestTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?,
                           protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val classNode = ClassNode()
        val reader = ClassReader(classfileBuffer)
        println("input bytes: ${classfileBuffer?.size}")
        reader.accept(classNode, 0)
        if (classNode.name.contains("example")) { //FIXME
            if (classNode.superName.contains("CoroutineImpl")) { //FIXME
                for (method in classNode.methods.map { it as MethodNode }) {
                    if (method.name == "doResume") {
                        println("doResume found in ${classNode.name}")
                        method.instructions.add(MethodInsnNode(Opcodes.INVOKESTATIC,
                                Type.getType(ObjectPrinter::class.java).internalName,
                                "foo", "()V", false))
                        //method.instructions.insert(printText("doResume found"))
                    }

                    val thisVar = method.localVariables.map { it as LocalVariableNode }.first { it.name == "this" }
                    val instructions = method.instructions.iterator()
                    val before = method.instructions.toArray()
                    println("before:")
                    before.forEach { println(it) }
                    while (instructions.hasNext()) {
                        val inst = instructions.next()
                        if (inst is MethodInsnNode) {
                            val type = Type.getMethodType(inst.desc)
                            if (inst.name != "<init>" && type.argumentTypes.size == 1
                                    && type.argumentTypes.first() == Type.getType(Continuation::class.java))
                                println("${inst.name} : ${type}")
                            //method.instructions.insert(inst, printVar(thisVar.index))
                            method.instructions.insert(inst, printText("text"))
                        }
                    }
                    val after = method.instructions.toArray()
                    println("after:")
                    after.forEach { println(it) }
                }
            } else {
                for (method in classNode.methods.map { it as MethodNode }) {
                    if (method.signature?.contains("Continuation") == true) { //FIXME
                        println("${method.name} : ${method.signature}")
                    }
                }
            }
            /*val result = writer.toByteArray()
            println("${className}_patched.class")
            if (result == null) {
                println("nothing to write")
            } else {
                File("${className}_patched.class").writeBytes(result)
            }*/
        }

        val writer = ClassWriter(0)
        classNode.accept(writer)
        println("output bytes: ${writer.toByteArray()?.size}")
        return writer.toByteArray()
    }
}

fun printText(text: String): InsnList {
    val list = InsnList()
    list.add(LdcInsnNode(text))
    val owner = Type.getType(ObjectPrinter::class.java).internalName
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "printText", "(Ljava/lang/String;)V", false))
    return list
}

fun printVar(varIndex: Int): InsnList {
    val list = InsnList()
    list.add(IntInsnNode(Opcodes.ILOAD, varIndex))
    val owner = Type.getType(ObjectPrinter::class.java).internalName
    list.add(MethodInsnNode(Opcodes.INVOKESTATIC, owner, "print", "(Ljava/lang/Object;)V", false))
    return list
}
