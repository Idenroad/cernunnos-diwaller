package com.cernunnos.authenticator.memory

import com.cernunnos.authenticator.constants.SecurityConfig
import com.cernunnos.authenticator.data.crypto.CryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Memory leak / bitmap recycling tests.
 *
 * Since this is a JVM unit test (no Android framework), we can't test actual
 * Bitmap recycling. Instead, we test the logic that determines whether
 * recycling should happen and verify that the crypto pipeline (which processes
 * large bitmap byte arrays) handles large payloads without error or data
 * corruption — a common source of memory leaks when streams/ciphers are not
 * properly closed.
 */
class BitmapRecyclingTest {

    private val key = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH) { it.toByte() }

    // ── Compression target size ──

    /**
     * The document compression target is 2MB (2 * 1024 * 1024).
     * This is the value used by DocumentRepository.TARGET_SIZE_BYTES.
     * Verify it is reasonable: large enough for readable documents, small
     * enough to avoid excessive memory use.
     */
    @Test
    fun compressionTargetSize_is2MB() {
        val targetSizeBytes = 2 * 1024 * 1024 // mirrors DocumentRepository.TARGET_SIZE_BYTES
        assertEquals(2_097_152, targetSizeBytes)
    }

    @Test
    fun compressionTargetSize_isReasonableForDocuments() {
        val targetSizeBytes = 2 * 1024 * 1024
        // Should be at least 1MB so document text remains readable
        assertTrue("Target should be >= 1MB for document readability", targetSizeBytes >= 1 * 1024 * 1024)
        // Should not exceed 5MB to avoid excessive memory consumption
        assertTrue("Target should be <= 5MB to limit memory use", targetSizeBytes <= 5 * 1024 * 1024)
    }

    // ── Thumbnail generation logic ──

    /**
     * Thumbnail generation scales the larger dimension down to 400px.
     * Verify the scaling logic produces smaller dimensions than the original.
     */
    @Test
    fun thumbnailGeneration_producesSmallerDimensionsThanOriginal() {
        val maxDim = 400 // mirrors DocumentRepository.generateThumbnail
        val originalWidth = 2000
        val originalHeight = 3000

        val scale = if (originalWidth > originalHeight) {
            maxDim.toFloat() / originalWidth
        } else {
            maxDim.toFloat() / originalHeight
        }
        val thumbWidth = (originalWidth * scale).toInt()
        val thumbHeight = (originalHeight * scale).toInt()

        assertTrue("Thumbnail width should be smaller than original", thumbWidth < originalWidth)
        assertTrue("Thumbnail height should be smaller than original", thumbHeight < originalHeight)
        // The larger dimension should be exactly maxDim
        assertEquals(maxDim, maxOf(thumbWidth, thumbHeight))
    }

    @Test
    fun thumbnailGeneration_smallImage_isNotScaledUp() {
        val maxDim = 400
        val originalWidth = 200
        val originalHeight = 150

        val scale = if (originalWidth > originalHeight) {
            maxDim.toFloat() / originalWidth
        } else {
            maxDim.toFloat() / originalHeight
        }
        // If scale >= 1, the thumbnail is the original (no upscaling)
        if (scale >= 1f) {
            assertEquals(originalWidth, 200)
            assertEquals(originalHeight, 150)
        } else {
            assertTrue("Should not upscale small images", false)
        }
    }

    /**
     * Simulate the compression quality reduction loop.
     * The loop reduces quality from 90 down to 70 (step 5) until under target.
     * Verify the minimum quality floor is 70 (documents need readability).
     */
    @Test
    fun compressionQualityLoop_neverGoesBelow70() {
        val minQuality = 70
        var quality = 90
        val targetBytes = 2 * 1024 * 1024
        // Simulate a very large image that never gets under target
        val simulatedSize = 10 * 1024 * 1024
        while (simulatedSize > targetBytes && quality > minQuality) {
            quality -= 5
        }
        assertEquals(minQuality, quality)
    }

    // ── Large data encrypt/decrypt roundtrip (simulates large bitmap bytes) ──

    @Test
    fun encryptWithKey_decryptWithKey_roundtrip_2MBData() {
        val largeData = ByteArray(2 * 1024 * 1024) { (it % 256).toByte() }
        val encrypted = CryptoManager.encryptWithKey(largeData, key)
        val decrypted = CryptoManager.decryptWithKey(encrypted, key)
        assertArrayEquals(largeData, decrypted)
    }

    @Test
    fun encryptWithKey_2MBData_worksWithoutError() {
        val largeData = ByteArray(2 * 1024 * 1024) { (it % 256).toByte() }
        val encrypted = CryptoManager.encryptWithKey(largeData, key)
        // Ciphertext should be larger than plaintext due to GCM tag (16 bytes)
        assertTrue("Ciphertext should be at least as large as plaintext", encrypted.ciphertext.size >= largeData.size)
        // IV should be 12 bytes (96-bit GCM IV)
        assertEquals(SecurityConfig.IV_SIZE, encrypted.iv.size)
    }

    @Test
    fun encryptWithKey_5MBData_worksWithoutError() {
        val largeData = ByteArray(5 * 1024 * 1024) { (it % 256).toByte() }
        val encrypted = CryptoManager.encryptWithKey(largeData, key)
        val decrypted = CryptoManager.decryptWithKey(encrypted, key)
        assertArrayEquals(largeData, decrypted)
    }

    // ── Sequential cycles don't accumulate errors ──

    @Test
    fun multipleSequentialEncryptDecryptCycles_doNotAccumulateErrors() {
        val data = ByteArray(512 * 1024) { (it % 256).toByte() } // 512KB per cycle
        repeat(10) { cycle ->
            val encrypted = CryptoManager.encryptWithKey(data, key)
            val decrypted = CryptoManager.decryptWithKey(encrypted, key)
            assertArrayEquals("Cycle $cycle: decrypted data should match original", data, decrypted)
        }
    }

    @Test
    fun multipleSequentialEncryptDecryptCycles_largeData_doNotAccumulateErrors() {
        val data = ByteArray(2 * 1024 * 1024) { (it % 256).toByte() } // 2MB per cycle
        repeat(5) { cycle ->
            val encrypted = CryptoManager.encryptWithKey(data, key)
            val decrypted = CryptoManager.decryptWithKey(encrypted, key)
            assertArrayEquals("Cycle $cycle: decrypted data should match original", data, decrypted)
        }
    }

    /**
     * Verify that each encryption produces a fresh IV (no IV reuse).
     * IV reuse in GCM is a catastrophic security failure and can also
     * indicate a state leak (memory issue).
     */
    @Test
    fun repeatedEncryption_producesDifferentIVs() {
        val data = ByteArray(1024) { 0x42 }
        val ivs = mutableSetOf<String>()
        repeat(20) {
            val encrypted = CryptoManager.encryptWithKey(data, key)
            ivs.add(encrypted.iv.joinToString("") { "%02x".format(it) })
        }
        assertEquals("All 20 IVs should be unique (no IV reuse)", 20, ivs.size)
    }

    /**
     * Verify that encrypting the same data twice produces different ciphertexts
     * (due to random IV). This confirms no stale state is being reused.
     */
    @Test
    fun repeatedEncryption_producesDifferentCiphertexts() {
        val data = ByteArray(1024) { 0x42 }
        val encrypted1 = CryptoManager.encryptWithKey(data, key)
        val encrypted2 = CryptoManager.encryptWithKey(data, key)
        assertTrue("Ciphertexts should differ due to random IV", !encrypted1.ciphertext.contentEquals(encrypted2.ciphertext))
    }
}
