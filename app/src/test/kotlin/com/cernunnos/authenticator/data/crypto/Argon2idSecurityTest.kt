package com.cernunnos.authenticator.data.crypto

import com.cernunnos.authenticator.constants.SecurityConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Security-focused tests for [Argon2id] key derivation.
 *
 * Uses small parameters to keep the tests fast.
 */
class Argon2idSecurityTest {

    private val fastParams = Argon2id.Params(
        iterations = 1,
        memory = 1024,
        parallelism = 1,
        outputLength = 32,
    )

    private val passphrase = "correct horse battery staple".toCharArray()
    private val salt = ByteArray(SecurityConfig.ARGON2_SALT_SIZE) { it.toByte() }

    // --- passphrase handling ---

    @Test
    fun deriveKey_doesNotLeavePassphraseInStringPool() {
        // The deriveKey function must accept a CharArray (not a String) so the
        // passphrase is not interned into the JVM string pool. Verify the
        // declared parameter type via reflection.
        val method = Argon2id::class.java.getDeclaredMethod(
            "deriveKey",
            CharArray::class.java,
            ByteArray::class.java,
            Argon2id.Params::class.java,
        )
        val paramType = method.parameterTypes[0]
        assertEquals(CharArray::class.java, paramType)
        // And it must successfully derive a key from a CharArray.
        val key = Argon2id.deriveKey(passphrase, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_samePassphraseSameSalt_producesSameKey() {
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey(passphrase.copyOf(), salt, fastParams)
        assertArrayEquals(k1, k2)
    }

    @Test
    fun deriveKey_differentSalt_producesDifferentKey() {
        val otherSalt = ByteArray(salt.size) { (it + 1).toByte() }
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey(passphrase, otherSalt, fastParams)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_differentPassphrase_producesDifferentKey() {
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey("a different passphrase".toCharArray(), salt, fastParams)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_emptyPassphrase_throwsOrHandles() {
        // An empty passphrase is accepted by Argon2id and produces a key of the
        // requested length (it does not throw).
        val key = Argon2id.deriveKey(CharArray(0), salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    // --- generateSalt ---

    @Test
    fun generateSalt_returns16Bytes() {
        val s = Argon2id.generateSalt()
        assertEquals(SecurityConfig.ARGON2_SALT_SIZE, s.size)
    }

    @Test
    fun generateSalt_isRandom() {
        val s1 = Argon2id.generateSalt()
        val s2 = Argon2id.generateSalt()
        assertEquals(SecurityConfig.ARGON2_SALT_SIZE, s1.size)
        assertEquals(SecurityConfig.ARGON2_SALT_SIZE, s2.size)
        assertFalse(s1.contentEquals(s2))
    }
}
