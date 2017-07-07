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
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import com.sun.javafx.scene.control.skin.FXVK.detach
import javassist.CannotCompileException
import javassist.tools.Callback.insertBefore
import javassist.CtClass
import javassist.CtMethod
import javassist.ClassPool
import java.lang.instrument.IllegalClassFormatException


class Agent {
    companion object {
        @JvmStatic
        fun premain(agentArgs: String?, inst: Instrumentation) {
            println("Agent started.")
            inst.addTransformer(JavasistTransformer())
        }
    }
}

class JavasistTransformer : ClassFileTransformer {
    override fun transform(loader: ClassLoader?, className: String?, classBeingRedefined: Class<*>?, protectionDomain: ProtectionDomain?, classfileBuffer: ByteArray?): ByteArray {
        val cc = ClassPool.getDefault().get(className?.replace('/', '.'))
        if (cc.name.contains("example")) {
            for (method in cc.methods) {
                val sig = method.genericSignature?: continue
                println("${method.name}: $sig")
            }
        }

        val method = cc.getDeclaredMethod("doResume")
        println(method)
        method.insertBefore("{System.out.println(\"label:\" + this.label);}")
        //method.insertBefore("{System.out.println(\"context:\" + this.context);}")
        method.insertBefore("{System.out.println(\"in thread with id:\" + Thread.currentThread().getId());}")
        method.insertBefore("{System.out.println(\"coroutine: \" + this.p$.toString());}")
        /*try {
            method.insertBefore("{mylibrary.ObjectPrinter.print(this);}")
        } catch (ex: CannotCompileException) {
            ex.printStackTrace()
        }*/
        //method.insertBefore("{System.out.println(this.p$.parentContext);}")
        val byteCode = cc.toBytecode()
        cc.detach()
        return byteCode
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
        if (name?.contains("resume") == true) {
            println("method: ${name}")
            return object : MethodNode(access, name, desc, signature, exceptions) {
                override fun visitEnd() {
                    println("visit end")
                    val locals = localVariables
                    if (locals != null) {
                        println("locals for method '${name}'")
                        for (i in locals) {
                            val lvn = i as LocalVariableNode
                            println("name: ${lvn.name}, sig: ${lvn.signature}, index: ${lvn.index}")
                        }
                    }
                    accept(this@MyClassVisitor)
                }
            }
        }
        /*if (argumentTypes.last() == Type.getType(Continuation::class.java)) {
            return PrintContinuationNameAdapter(access, method, cv.visitMethod(access, name, desc, signature, exceptions))
        }*/
        return cv.visitMethod(access, name, desc, signature, exceptions)
    }
}
