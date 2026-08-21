package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

/**
 * Edge-case tests for IOUtils bounded reads.
 */
class IOUtilsTest {

    @Test
    fun readBounded_underLimit_returnsAllData() {
        val data = ByteArray(100) { it.toByte() }
        val stream = java.io.ByteArrayInputStream(data)
        val result = IOUtils.readBounded(stream, 200)
        assertArrayEquals(data, result)
    }

    @Test
    fun readBounded_exactLimit_returnsAllData() {
        val data = ByteArray(100) { it.toByte() }
        val stream = java.io.ByteArrayInputStream(data)
        val result = IOUtils.readBounded(stream, 100)
        assertArrayEquals(data, result)
    }

    @Test(expected = java.io.IOException::class)
    fun readBounded_overLimit_throws() {
        val data = ByteArray(200) { it.toByte() }
        val stream = java.io.ByteArrayInputStream(data)
        IOUtils.readBounded(stream, 100)
    }

    @Test
    fun readBounded_emptyStream_returnsEmpty() {
        val stream = java.io.ByteArrayInputStream(ByteArray(0))
        val result = IOUtils.readBounded(stream, 100)
        assertEquals(0, result.size)
    }

    @Test
    fun readBounded_largeChunkedStream_returnsAllData() {
        val data = ByteArray(10 * 1024) { (it % 256).toByte() }
        val stream = java.io.ByteArrayInputStream(data)
        val result = IOUtils.readBounded(stream, IOUtils.MAX_NETWORK_BYTES)
        assertArrayEquals(data, result)
    }
}
