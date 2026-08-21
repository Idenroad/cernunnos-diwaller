package com.cernunnos.authenticator.data.crypto

import com.cernunnos.authenticator.constants.SecurityConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * Unit tests for [CryptoManager] (AES-256-GCM).
 *
 * Note: [CryptoManager.encrypt]/[decrypt] derive the key via [Argon2id] using the
 * default (production) parameters, so these tests are slower than the
 * [Argon2idTest] counterparts. The `encryptWithKey`/`decryptWithKey` paths bypass
 * Argon2id entirely and are fast.
 */
class CryptoManagerTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val salt = ByteArray(SecurityConfig.ARGON2_SALT_SIZE) { it.toByte() }
    private val plaintext = "secret TOTP seed".toByteArray(Charsets.UTF_8)

    // --- encrypt / decrypt round trip ---

    @Test
    fun encrypt_decrypt_roundTrip_returnsOriginalPlaintext() {
        val encrypted = CryptoManager.encrypt(plaintext, passphrase, salt)
        val decrypted = CryptoManager.decrypt(encrypted, passphrase)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt_emptyPlaintext_works() {
        val empty = ByteArray(0)
        val encrypted = CryptoManager.encrypt(empty, passphrase, salt)
        val decrypted = CryptoManager.decrypt(encrypted, passphrase)
        assertArrayEquals(empty, decrypted)
        // GCM tag is appended even for empty plaintext.
        assertTrue(encrypted.ciphertext.size > 0)
    }

    @Test
    fun encrypt_differentPassphrases_differentCiphertext() {
        val otherPassphrase = "a different passphrase".toCharArray()
        val e1 = CryptoManager.encrypt(plaintext, passphrase, salt)
        val e2 = CryptoManager.encrypt(plaintext, otherPassphrase, salt)
        // IVs are random so ciphertexts will differ.
        assertFalse(e1.ciphertext.contentEquals(e2.ciphertext))
    }

    @Test
    fun encrypt_differentSalts_differentCiphertext() {
        val otherSalt = ByteArray(salt.size) { (it + 1).toByte() }
        val e1 = CryptoManager.encrypt(plaintext, passphrase, salt)
        val e2 = CryptoManager.encrypt(plaintext, passphrase, otherSalt)
        assertFalse(e1.ciphertext.contentEquals(e2.ciphertext))
    }

    @Test
    fun decrypt_wrongPassphrase_throws() {
        val encrypted = CryptoManager.encrypt(plaintext, passphrase, salt)
        try {
            CryptoManager.decrypt(encrypted, "wrong passphrase".toCharArray())
            fail("Expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // expected: GCM tag verification fails
        }
    }

    @Test
    fun decrypt_corruptedCiphertext_throws() {
        val encrypted = CryptoManager.encrypt(plaintext, passphrase, salt)
        val corrupted = encrypted.copy(
            ciphertext = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        )
        try {
            CryptoManager.decrypt(corrupted, passphrase)
            fail("Expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun decrypt_corruptedIv_throws() {
        val encrypted = CryptoManager.encrypt(plaintext, passphrase, salt)
        val corrupted = encrypted.copy(
            iv = encrypted.iv.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        )
        try {
            CryptoManager.decrypt(corrupted, passphrase)
            fail("Expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    // --- encryptWithKey / decryptWithKey ---

    @Test
    fun encryptWithKey_decryptWithKey_roundTrip() {
        val key = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH) { it.toByte() }
        val encrypted = CryptoManager.encryptWithKey(plaintext, key)
        val decrypted = CryptoManager.decryptWithKey(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun encryptWithKey_emptyKey_throws() {
        val emptyKey = ByteArray(0)
        try {
            CryptoManager.encryptWithKey(plaintext, emptyKey)
            fail("Expected exception for empty key")
        } catch (e: IllegalArgumentException) {
            // expected: AES requires a valid key length
        }
    }

    @Test
    fun encryptWithKey_emptyPlaintext_works() {
        val empty = ByteArray(0)
        val key = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH) { it.toByte() }
        val encrypted = CryptoManager.encryptWithKey(empty, key)
        val decrypted = CryptoManager.decryptWithKey(encrypted, key)
        assertArrayEquals(empty, decrypted)
    }

    @Test
    fun decryptWithKey_wrongKey_throws() {
        val key = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH) { it.toByte() }
        val wrongKey = ByteArray(SecurityConfig.ARGON2_OUTPUT_LENGTH) { (it + 7).toByte() }
        val encrypted = CryptoManager.encryptWithKey(plaintext, key)
        try {
            CryptoManager.decryptWithKey(encrypted, wrongKey)
            fail("Expected AEADBadTagException")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    // --- IV randomness & sizes ---

    @Test
    fun encrypt_producesDifferentIVsForSameInput() {
        val e1 = CryptoManager.encrypt(plaintext, passphrase, salt)
        val e2 = CryptoManager.encrypt(plaintext, passphrase, salt)
        assertFalse(e1.iv.contentEquals(e2.iv))
        // Ciphertexts also differ because of the random IV.
        assertFalse(e1.ciphertext.contentEquals(e2.ciphertext))
    }

    @Test
    fun encrypt_saltSizeIsCorrect() {
        val encrypted = CryptoManager.encrypt(plaintext, passphrase, salt)
        assertEquals(SecurityConfig.IV_SIZE, encrypted.iv.size)
        assertEquals(salt.size, encrypted.salt.size)
    }
}
