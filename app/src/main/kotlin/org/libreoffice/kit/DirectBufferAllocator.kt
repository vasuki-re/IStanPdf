package org.libreoffice.kit

import java.nio.ByteBuffer

object DirectBufferAllocator {

    @Suppress("unused")
    private val LOGTAG: String = DirectBufferAllocator::class.java.simpleName

    fun allocate(size: Int): ByteBuffer {
        val directBuffer: ByteBuffer? = ByteBuffer.allocateDirect(size)
        if (directBuffer == null) {
            if (size <= 0) {
                throw IllegalArgumentException("Invalid allocation size: $size")
            } else {
                throw OutOfMemoryError("allocateDirectBuffer() returned null")
            }
        } else if (!directBuffer.isDirect) {
            throw AssertionError("allocateDirectBuffer() did not return a direct buffer")
        }
        return directBuffer
    }

    fun free(buffer: ByteBuffer?): ByteBuffer? {
        if (buffer == null) {
            return null
        }
        if (!buffer.isDirect) {
            throw IllegalArgumentException("ByteBuffer must be direct")
        }
        return null
    }

    fun guardedAllocate(size: Int): ByteBuffer? {
        return try {
            allocate(size)
        } catch (_: OutOfMemoryError) {
            null
        }
    }
}
