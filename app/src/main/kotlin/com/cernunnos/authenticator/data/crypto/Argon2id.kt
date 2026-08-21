package com.cernunnos.authenticator.data.crypto

import com.cernunnos.authenticator.constants.*
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom

/**
 * Argon2id key derivation using Bouncy Castle (pure Java, Android-compatible).
 * Deterministic: same passphrase + salt = same key.
 */
object Argon2id {

    data class Params(
        val iterations: Int = SecurityConfig.ARGON2_ITERATIONS,
        val memory: Int = SecurityConfig.ARGON2_MEMORY_KB,   // 96 MB (in KB) — robust without OOM on low-end devices
        val parallelism: Int = SecurityConfig.ARGON2_PARALLELISM,
        val outputLength: Int = SecurityConfig.ARGON2_OUTPUT_LENGTH, // AES-256
    )

    /**
     * Derive a key from passphrase + salt.
     * Returns raw key bytes (outputLength bytes).
     */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, params: Params = Params()): ByteArray {
        val bouncyParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memory)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(bouncyParams)
        val output = ByteArray(params.outputLength)
        // Convert CharArray → UTF-8 ByteArray WITHOUT creating an intermediate
        // String (which would be immutable and impossible to zero).
        // We encode each char manually to UTF-8 bytes.
        val passphraseBytes = charArrayToUtf8Bytes(passphrase)
        try {
            generator.generateBytes(passphraseBytes, output)
        } finally {
            passphraseBytes.fill(0)
        }
        return output
    }

    /**
     * Derive key with explicit Argon2id parameters.
     * Used for Aegis import where parameters differ from our defaults.
     */
    fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        memory: Int,
        iterations: Int,
        parallelism: Int,
        outputLength: Int = SecurityConfig.ARGON2_OUTPUT_LENGTH,
    ): ByteArray {
        return deriveKey(passphrase, salt, Params(iterations, memory, parallelism, outputLength))
    }

    fun generateSalt(size: Int = SecurityConfig.ARGON2_SALT_SIZE): ByteArray {
        return ByteArray(size).also { SecureRandom().nextBytes(it) }
    }

    /**
     * Convert a CharArray to UTF-8 bytes WITHOUT creating an intermediate String.
     * String is immutable on the JVM and cannot be zeroed, so we encode manually.
     * The caller is responsible for zeroing the returned ByteArray after use.
     *
     * Handles surrogate pairs correctly: a high surrogate (0xD800-0xDBFF) followed
     * by a low surrogate (0xDC00-0xDFFF) is combined into a single code point and
     * encoded as a 4-byte UTF-8 sequence. Isolated surrogates are encoded as
     * 3-byte sequences (replacement character behavior is avoided to keep
     * determinism).
     */
    private fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
        // Pre-calculate exact UTF-8 byte length to avoid reallocation
        var byteLen = 0
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c.isHighSurrogate() && i + 1 < chars.size && chars[i + 1].isLowSurrogate()) {
                byteLen += 4
                i += 2
            } else {
                byteLen += when {
                    c.code <= 0x7F -> 1
                    c.code <= 0x7FF -> 2
                    else -> 3
                }
                i++
            }
        }
        val out = ByteArray(byteLen)
        var idx = 0
        i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c.isHighSurrogate() && i + 1 < chars.size && chars[i + 1].isLowSurrogate()) {
                // Combine surrogate pair into a code point
                val cp = 0x10000 + ((c.code - 0xD800) shl 10) + (chars[i + 1].code - 0xDC00)
                out[idx++] = (0xF0 or (cp shr 18)).toByte()
                out[idx++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                out[idx++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                out[idx++] = (0x80 or (cp and 0x3F)).toByte()
                i += 2
            } else {
                val cp = c.code
                when {
                    cp <= 0x7F -> {
                        out[idx++] = cp.toByte()
                    }
                    cp <= 0x7FF -> {
                        out[idx++] = (0xC0 or (cp shr 6)).toByte()
                        out[idx++] = (0x80 or (cp and 0x3F)).toByte()
                    }
                    else -> {
                        out[idx++] = (0xE0 or (cp shr 12)).toByte()
                        out[idx++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                        out[idx++] = (0x80 or (cp and 0x3F)).toByte()
                    }
                }
                i++
            }
        }
        return out
    }
}
