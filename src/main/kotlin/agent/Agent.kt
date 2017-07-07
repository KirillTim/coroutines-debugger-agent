package agent

import javassist.ClassPool
import javassist.CtMethod
import javassist.bytecode.Bytecode
import javassist.bytecode.SignatureAttribute
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import java.io.File
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.nio.file.Paths
import java.security.ProtectionDomain
import kotlin.coroutines.experimental.Continuation


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
                if (isContinuationMethod(method)) {
                    method.insertBefore("{System.out.println(\"in ${method}\");}")
                    method.insertBefore("{mylibrary.ObjectPrinter.print(\$\$);}")
                    method.insertAfter("{System.out.println(\"after ${method}\");}")
                    //method.insertAfter("{mylibrary.ObjectPrinter.print(\$r);}")
                } else if (method.name == "doResume") {
                    method.insertBefore("{mylibrary.ObjectPrinter.print(this.p$);}")
                    method.insertBefore("{mylibrary.ObjectPrinter.print(this);}")

                    /*method.instrument(object : ExprEditor() {
                        override fun edit(m: MethodCall?) {
                            m ?: return
                            if (m.signature.contains("Continuation")) {
                                println(">>>>>>> ${m.signature}, ${m.methodName}, line: ${m.lineNumber}")
                                println(">>>>>>> index of bytecode: ${m.indexOfBytecode()}")
                                println(m.method.methodInfo.codeAttribute.codeLength)
                                m.method.insertAfter("{System.out.println(\"after ${method}\");}")
                                println(m.method.methodInfo.codeAttribute.codeLength)
                                //method.insertAt(m.lineNumber, "{System.out.println(\"WHAT\");}")
                            }
                            //m.replace(m.where().signature)
                        }
                    })*/
                }
            }
            val s = Paths.get("").toAbsolutePath().toString()
            val cx = cc.toBytecode().copyOf()
            val file = File(s + "/${className}_modified.class")
            file.writeBytes(cx)
        }
        val byteCode = cc.toBytecode()
        cc.detach()
        return byteCode
    }
}


fun isContinuationMethod(method: CtMethod?): Boolean {
    val sig = method?.signature ?: return false
    val params = SignatureAttribute.toMethodSignature(sig).parameterTypes
    if (params.size != 1)
        return false
    return (params.first() as? SignatureAttribute.ClassType)?.name == Continuation::class.java.canonicalName
}