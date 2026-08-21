package org.libreoffice.kit

import java.nio.ByteBuffer

class Office(private val handle: ByteBuffer) {

    private var messageCallback: MessageCallback? = null

    init {
        bindMessageCallback()
    }

    private external fun bindMessageCallback()

    external fun getError(): String

    private external fun documentLoadNative(url: String): ByteBuffer?

    fun documentLoad(url: String): Document? {
        val documentHandle = documentLoadNative(url) ?: return null
        return Document(documentHandle)
    }

    external fun destroy()
    external fun destroyAndExit()
    external fun setDocumentPassword(url: String, pwd: String)
    external fun setOptionalFeatures(options: Long)

    fun setMessageCallback(messageCallback: MessageCallback?) {
        this.messageCallback = messageCallback
    }

    @Suppress("unused")
    private fun messageRetrievedLOKit(signalNumber: Int, payload: String) {
        messageCallback?.messageRetrieved(signalNumber, payload)
    }

    fun interface MessageCallback {
        fun messageRetrieved(signalNumber: Int, payload: String)
    }
}
