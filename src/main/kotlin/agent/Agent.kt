package agent

import org.objectweb.asm.*
import org.objectweb.asm.ClassWriter.COMPUTE_MAXS
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.LocalVariablesSorter
import org.objectweb.asm.commons.Method
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import kotlin.coroutines.experimental.Continuation
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
        val method = Method(name, desc)
        val argumentTypes = method.argumentTypes
        if (argumentTypes.last() == Type.getType(Continuation::class.java)) {
            return PrintContinuationNameAdapter(access, method, cv.visitMethod(access, name, desc, signature, exceptions))
        }
        return cv.visitMethod(access, name, desc, signature, exceptions)
    }
}
