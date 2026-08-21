package com.cernunnos.authenticator.data.crypto

import org.junit.Test
import org.junit.Assert.*
import java.security.SecureRandom

/**
 * Additional crypto edge-case tests for production readiness.
 */
class CryptoEdgeCasesTest {

    @Test
    fun encrypt_decrypt_emptyPlaintext_roundTrip() {
        val salt = Argon2id.generateSalt()
        val pass = "testPass123".toCharArray()
        try {
            val encrypted = CryptoManager.encrypt(ByteArray(0), pass, salt)
            val decrypted = CryptoManager.decrypt(encrypted, pass)
            assertEquals(0, decrypted.size)
        } finally {
            pass.fill(0.toChar())
        }
    }

    @Test
    fun encrypt_decrypt_largePayload_roundTrip() {
        val salt = Argon2id.generateSalt()
        val pass = "testPass123".toCharArray()
        val data = ByteArray(1024 * 1024) { it.toByte() } // 1 MB
        try {
            val encrypted = CryptoManager.encrypt(data, pass, salt)
            val decrypted = CryptoManager.decrypt(encrypted, pass)
            assertArrayEquals(data, decrypted)
        } finally {
            pass.fill(0.toChar())
        }
    }

    @Test
    fun encryptWithKey_decryptWithKey_emptyPlaintext_roundTrip() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val encrypted = CryptoManager.encryptWithKey(ByteArray(0), key)
            val decrypted = CryptoManager.decryptWithKey(encrypted, key)
            assertEquals(0, decrypted.size)
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun encryptWithKey_decryptWithKey_largePayload_roundTrip() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = ByteArray(512 * 1024) { (it % 256).toByte() } // 512 KB
        try {
            val encrypted = CryptoManager.encryptWithKey(data, key)
            val decrypted = CryptoManager.decryptWithKey(encrypted, key)
            assertArrayEquals(data, decrypted)
        } finally {
            key.fill(0)
        }
    }

    @Test(expected = Exception::class)
    fun decryptWithKey_wrongKey_throws() {
        val key1 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val key2 = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val encrypted = CryptoManager.encryptWithKey("secret data".toByteArray(), key1)
            // GCM tag verification will fail
            CryptoManager.decryptWithKey(encrypted, key2)
        } finally {
            key1.fill(0)
            key2.fill(0)
        }
    }

    @Test(expected = Exception::class)
    fun decryptWithKey_truncatedCiphertext_throws() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val encrypted = CryptoManager.encryptWithKey("secret data".toByteArray(), key)
            // Truncate ciphertext (remove GCM tag)
            val truncated = CryptoManager.EncryptedData(
                encrypted.salt,
                encrypted.iv,
                encrypted.ciphertext.copyOf(encrypted.ciphertext.size - 16),
            )
            CryptoManager.decryptWithKey(truncated, key)
        } finally {
            key.fill(0)
        }
    }

    @Test(expected = Exception::class)
    fun decryptWithKey_tamperedCiphertext_throws() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val encrypted = CryptoManager.encryptWithKey("secret data".toByteArray(), key)
            // Flip a bit in the ciphertext
            val tampered = encrypted.ciphertext.copyOf()
            tampered[0] = (tampered[0].toInt() xor 0x01).toByte()
            CryptoManager.decryptWithKey(
                CryptoManager.EncryptedData(encrypted.salt, encrypted.iv, tampered),
                key,
            )
        } finally {
            key.fill(0)
        }
    }

    @Test(expected = Exception::class)
    fun decryptWithKey_tamperedIV_throws() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val encrypted = CryptoManager.encryptWithKey("secret data".toByteArray(), key)
            // Flip a bit in the IV
            val tamperedIv = encrypted.iv.copyOf()
            tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()
            CryptoManager.decryptWithKey(
                CryptoManager.EncryptedData(encrypted.salt, tamperedIv, encrypted.ciphertext),
                key,
            )
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun encryptWithKey_producesDifferentIVs_eachCall() {
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val data = "test data".toByteArray()
        try {
            val enc1 = CryptoManager.encryptWithKey(data, key)
            val enc2 = CryptoManager.encryptWithKey(data, key)
            // IVs must be different (random)
            assertFalse(enc1.iv.contentEquals(enc2.iv))
            // But both decrypt to the same plaintext
            assertArrayEquals(data, CryptoManager.decryptWithKey(enc1, key))
            assertArrayEquals(data, CryptoManager.decryptWithKey(enc2, key))
        } finally {
            key.fill(0)
        }
    }

    @Test
    fun argon2id_samePassphraseDifferentSalt_producesDifferentKeys() {
        val pass = "testPass123".toCharArray()
        val salt1 = Argon2id.generateSalt()
        val salt2 = Argon2id.generateSalt()
        try {
            val key1 = Argon2id.deriveKey(pass, salt1)
            val key2 = Argon2id.deriveKey(pass, salt2)
            assertFalse(key1.contentEquals(key2))
            key1.fill(0)
            key2.fill(0)
        } finally {
            pass.fill(0.toChar())
        }
    }

    @Test
    fun argon2id_samePassphraseSameSalt_producesSameKey() {
        val pass = "testPass123".toCharArray()
        val salt = Argon2id.generateSalt()
        try {
            val key1 = Argon2id.deriveKey(pass, salt)
            val key2 = Argon2id.deriveKey(pass, salt)
            assertArrayEquals(key1, key2)
            key1.fill(0)
            key2.fill(0)
        } finally {
            pass.fill(0.toChar())
        }
    }

    @Test
    fun argon2id_generatedSalt_is16Bytes() {
        val salt = Argon2id.generateSalt()
        assertEquals(16, salt.size)
    }

    @Test
    fun argon2id_generatedSalts_areRandom() {
        val salts = (1..10).map { Argon2id.generateSalt() }
        // All salts should be different
        for (i in salts.indices) {
            for (j in (i + 1) until salts.size) {
                assertFalse("Salts $i and $j are identical", salts[i].contentEquals(salts[j]))
            }
        }
    }
}
