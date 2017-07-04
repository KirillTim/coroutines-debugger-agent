package agent

import org.objectweb.asm.*
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain

class Agent {
    companion object {
        // the idea is to make this code as simple is possible.
        // no need to use asm.commons.*
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
        val writer = ClassWriter(reader, COMPUTE_MAXS)
        val visitor = MyClassVisitor(writer)
        reader.accept(visitor, 0)
        return writer.toByteArray()
    }
}

class MyClassVisitor(cv: ClassVisitor) : ClassVisitor(Opcodes.ASM5, cv) {
    override fun visitMethod(access: Int, name: String?, desc: String?, signature: String?,
                             exceptions: Array<out String>?): MethodVisitor? {
        cv ?: return null // just in case
        return MyMethodVisitor(cv.visitMethod(access, name, desc, signature, exceptions))
    }
}

class MyMethodVisitor(mv: MethodVisitor) : MethodVisitor(Opcodes.ASM5, mv) {
    companion object {
        private val METHOD_OPCODE = Opcodes.INVOKESTATIC
        private val METHOD_OWNER = "example/CoroutineExampleKt"
        private val METHOD_NAME = "test"
        private val METHOD_SIGNATURE = "(Lkotlin/coroutines/experimental/Continuation;)Ljava/lang/Object;"

        private data class MyInstruction(val owner: String, val name: String, val desc: String)

        private val systemOut = MyInstruction("java/lang/System", "out", "Ljava/io/PrintStream;")
        private val outPrintln = MyInstruction("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
    }

    override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
        if (opcode == METHOD_OPCODE && owner == METHOD_OWNER && name == METHOD_NAME && desc == METHOD_SIGNATURE) {
            mv.visitFieldInsn(Opcodes.GETSTATIC, systemOut.owner, systemOut.name, systemOut.desc)
            mv.visitLdcInsn("Test detected")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, outPrintln.owner, outPrintln.name, outPrintln.desc, false)
        }
        mv.visitMethodInsn(opcode, owner, name, desc, itf)
    }
}