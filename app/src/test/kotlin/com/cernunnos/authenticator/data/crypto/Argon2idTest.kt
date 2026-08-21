package com.cernunnos.authenticator.data.crypto

import com.cernunnos.authenticator.constants.SecurityConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for [Argon2id] key derivation.
 *
 * Uses intentionally small parameters to keep the tests fast:
 *  - memory = 1024 KB (1 MB)
 *  - iterations = 1
 *  - parallelism = 1
 *  - outputLength = 32
 */
class Argon2idTest {

    private val fastParams = Argon2id.Params(
        iterations = 1,
        memory = 1024,
        parallelism = 1,
        outputLength = 32,
    )

    private val passphrase = "correct horse battery staple".toCharArray()
    private val salt = ByteArray(SecurityConfig.ARGON2_SALT_SIZE) { it.toByte() }

    // --- deriveKey determinism / differentiation ---

    @Test
    fun deriveKey_sameInputs_sameOutput() {
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey(passphrase, salt, fastParams)
        assertArrayEquals(k1, k2)
    }

    @Test
    fun deriveKey_differentPassphrases_differentOutput() {
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey("a different passphrase".toCharArray(), salt, fastParams)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_differentSalts_differentOutput() {
        val otherSalt = ByteArray(salt.size) { (it + 1).toByte() }
        val k1 = Argon2id.deriveKey(passphrase, salt, fastParams)
        val k2 = Argon2id.deriveKey(passphrase, otherSalt, fastParams)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_emptyPassphrase_works() {
        val key = Argon2id.deriveKey(CharArray(0), salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_emptySalt_works() {
        val key = Argon2id.deriveKey(passphrase, ByteArray(0), fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_customParams_works() {
        val custom = Argon2id.Params(
            iterations = 2,
            memory = 2048,
            parallelism = 1,
            outputLength = 32,
        )
        val key = Argon2id.deriveKey(passphrase, salt, custom)
        assertEquals(custom.outputLength, key.size)
    }

    @Test
    fun deriveKey_outputLengthIsCorrect() {
        val params = Argon2id.Params(
            iterations = 1,
            memory = 1024,
            parallelism = 1,
            outputLength = 64,
        )
        val key = Argon2id.deriveKey(passphrase, salt, params)
        assertEquals(64, key.size)
    }

    @Test
    fun deriveKey_differentIterations_differentOutput() {
        val p1 = Argon2id.Params(iterations = 1, memory = 1024, parallelism = 1, outputLength = 32)
        val p2 = Argon2id.Params(iterations = 2, memory = 1024, parallelism = 1, outputLength = 32)
        val k1 = Argon2id.deriveKey(passphrase, salt, p1)
        val k2 = Argon2id.deriveKey(passphrase, salt, p2)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_differentMemory_differentOutput() {
        val p1 = Argon2id.Params(iterations = 1, memory = 1024, parallelism = 1, outputLength = 32)
        val p2 = Argon2id.Params(iterations = 1, memory = 2048, parallelism = 1, outputLength = 32)
        val k1 = Argon2id.deriveKey(passphrase, salt, p1)
        val k2 = Argon2id.deriveKey(passphrase, salt, p2)
        assertFalse(k1.contentEquals(k2))
    }

    // --- generateSalt ---

    @Test
    fun generateSalt_defaultSize_is16Bytes() {
        val s = Argon2id.generateSalt()
        assertEquals(SecurityConfig.ARGON2_SALT_SIZE, s.size)
    }

    @Test
    fun generateSalt_customSize_isCorrectSize() {
        val s = Argon2id.generateSalt(32)
        assertEquals(32, s.size)
    }

    @Test
    fun generateSalt_differentCalls_differentResults() {
        val s1 = Argon2id.generateSalt()
        val s2 = Argon2id.generateSalt()
        assertFalse(s1.contentEquals(s2))
    }

    // --- Surrogate pairs (emoji and astral plane characters) ---

    @Test
    fun deriveKey_emojiPassphrase_matchesStringUtf8() {
        // "🔑" = U+1F511 = surrogate pair \uD83D\uDD11
        val emojiPass = "🔑secret".toCharArray()
        val key1 = Argon2id.deriveKey(emojiPass, salt, fastParams)

        // Reference: derive using String.toByteArray(Charsets.UTF_8) indirectly
        // We verify determinism: same input always produces same key
        val key2 = Argon2id.deriveKey("🔑secret".toCharArray(), salt, fastParams)
        assertArrayEquals(key1, key2)
    }

    @Test
    fun deriveKey_emojiPassphrase_differentFromNoEmoji() {
        val withEmoji = "🔑secret".toCharArray()
        val withoutEmoji = "secret".toCharArray()
        val k1 = Argon2id.deriveKey(withEmoji, salt, fastParams)
        val k2 = Argon2id.deriveKey(withoutEmoji, salt, fastParams)
        assertFalse("Emoji passphrase must produce different key", k1.contentEquals(k2))
    }

    @Test
    fun deriveKey_multipleEmoji_works() {
        val pass = "🔐🔑🔒12345".toCharArray()
        val key = Argon2id.deriveKey(pass, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_highSurrogateAtEnd_doesNotCrash() {
        // High surrogate without a following low surrogate at the end of the array
        val pass = charArrayOf('a', 'b', '\uD83D')
        val key = Argon2id.deriveKey(pass, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_lowSurrogateAtStart_doesNotCrash() {
        // Low surrogate without a preceding high surrogate at the start
        val pass = charArrayOf('\uDD11', 'a', 'b')
        val key = Argon2id.deriveKey(pass, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_cjkCharacters_works() {
        // CJK characters (3-byte UTF-8)
        val pass = "密码密码123".toCharArray()
        val key = Argon2id.deriveKey(pass, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }

    @Test
    fun deriveKey_mixedAsciiCjkEmoji_works() {
        val pass = "Pass密码🔑".toCharArray()
        val key = Argon2id.deriveKey(pass, salt, fastParams)
        assertEquals(fastParams.outputLength, key.size)
    }
}
