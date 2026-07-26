package vasuki.istanpdf.libreoffice

import org.libreoffice.kit.Document
import java.util.concurrent.TimeUnit

object UnoCommandHelper {
    private const val DEFAULT_TIMEOUT_MS = 1000L
    private val lock = Any()
    @Volatile
    private var commandComplete = false

    private val SHARED_CALLBACK = Document.MessageCallback { signalNumber, _ ->
        if (signalNumber == Document.CALLBACK_UNO_COMMAND_RESULT) {
            synchronized(lock) {
                commandComplete = true
                (lock as Object).notifyAll()
            }
        }
    }

    @Throws(InterruptedException::class)
    fun postAndWait(doc: Document, command: String) {
        postAndWait(doc, command, "", DEFAULT_TIMEOUT_MS)
    }

    @Throws(InterruptedException::class)
    fun postAndWait(doc: Document, command: String, arguments: String, timeoutMs: Long) {
        synchronized(lock) {
            commandComplete = false
        }

        doc.setMessageCallback(SHARED_CALLBACK)
        doc.postUnoCommand(command, arguments, true)

        synchronized(lock) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (!commandComplete) {
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining <= 0) break
                (lock as Object).wait(remaining)
            }
        }
    }

    fun postFireAndForget(doc: Document, command: String) {
        doc.postUnoCommand(command, "", false)
    }
}
