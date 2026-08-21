package com.cernunnos.authenticator.util

import java.io.IOException
import java.io.InputStream

/**
 * Stream utilities with bounded reads to prevent OOM on untrusted inputs.
 */
object IOUtils {

    /** Default maximum response size for cloud/network reads (16 MB). */
    const val MAX_NETWORK_BYTES = 16L * 1024 * 1024

    /** Maximum size for P2P Wi-Fi Direct transfers (4 MB). */
    const val MAX_P2P_BYTES = 4L * 1024 * 1024

    /**
     * Read at most [maxBytes] bytes from this stream into a ByteArray.
     * Throws [IOException] if the stream contains more data than [maxBytes].
     */
    fun readBounded(stream: InputStream, maxBytes: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("Stream exceeds maximum allowed size ($maxBytes bytes)")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
