package mylibrary

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import kotlin.coroutines.experimental.CoroutineContext
import kotlinx.coroutines.experimental.nio.aAccept
import kotlinx.coroutines.experimental.nio.aWrite
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * @author Kirill Timofeev
 */

object SocketWriter {
    fun start(port: Int, stacks: Map<CoroutineContext, CoroutineStackImpl>) = thread {
        runBlocking {
            val serverChannel = AsynchronousServerSocketChannel
                    .open()
                    .bind(InetSocketAddress(port))
            while (true) {
                val client = serverChannel?.aAccept()
                launch(context) {
                    try {
                        var string = stacks.values.joinToString("\n\n", transform = { it.prettyPrint() })
                        if (string.isEmpty()) {
                            string = "nothing to show"
                        }
                        val buffer = ByteBuffer.wrap(string.toByteArray())
                        client?.aWrite(buffer)
                        client?.close()
                    } catch (ex: Throwable) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }
}
