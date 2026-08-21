package com.cernunnos.authenticator.data.crypto

import com.cernunnos.authenticator.constants.*
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption/decryption.
 * Used to encrypt TOTP seeds with the master key derived from Argon2id.
 */
object CryptoManager {

    private const val GCM_TAG_BITS = SecurityConfig.GCM_TAG_BITS
    private const val IV_SIZE = SecurityConfig.IV_SIZE // 96-bit IV for GCM

    data class EncryptedData(val salt: ByteArray, val iv: ByteArray, val ciphertext: ByteArray)

    /**
     * Encrypt data with a derived key.
     * The salt is used for Argon2id key derivation from the passphrase.
     */
    fun encrypt(plaintext: ByteArray, passphrase: CharArray, salt: ByteArray): EncryptedData {
        val key = Argon2id.deriveKey(passphrase, salt)
        try {
            val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(plaintext)
            return EncryptedData(salt, iv, ciphertext)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Decrypt data with a derived key.
     */
    fun decrypt(encrypted: EncryptedData, passphrase: CharArray): ByteArray {
        val key = Argon2id.deriveKey(passphrase, encrypted.salt)
        try {
            val cipher = Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, encrypted.iv))
            return cipher.doFinal(encrypted.ciphertext)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Encrypt with a pre-derived key (no Argon2id).
     *
     * NOTE: The `key` ByteArray is intentionally NOT zeroed here. The caller
     * (e.g. TotpRepository / EncryptedStore) owns the key lifecycle and reuses
     * it across multiple operations, then zeroes it in its own `lock()` /
     * cleanup path. Zeroing it here would break subsequent operations and
     * cause premature key loss. Key zeroing is the responsibility of the
     * caller, not CryptoManager.
     */
    fun encryptWithKey(plaintext: ByteArray, key: ByteArray): EncryptedData {
        val salt = Argon2id.generateSalt() // placeholder, not used for derivation
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedData(salt, iv, ciphertext)
    }

    /**
     * Decrypt with a pre-derived key (no Argon2id).
     *
     * NOTE: The `key` ByteArray is intentionally NOT zeroed here — see the
     * note on [encryptWithKey]. The caller owns the key lifecycle and is
     * responsible for zeroing it (e.g. TotpRepository.lock()).
     */
    fun decryptWithKey(encrypted: EncryptedData, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, encrypted.iv))
        return cipher.doFinal(encrypted.ciphertext)
    }

    /**
     * Decrypt with a raw key, explicit nonce, and combined ciphertext+tag.
     * Used for Aegis import where the format differs slightly.
     */
    fun decryptWithRawKey(key: ByteArray, nonce: ByteArray, ciphertextWithTag: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(SecurityConfig.CIPHER_AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertextWithTag)
    }
}
