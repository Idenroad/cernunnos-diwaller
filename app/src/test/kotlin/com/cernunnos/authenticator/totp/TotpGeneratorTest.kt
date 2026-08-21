package com.cernunnos.authenticator.totp

import com.cernunnos.authenticator.util.Base32Codec
import org.junit.Test
import org.junit.Assert.*
import java.lang.IllegalArgumentException

/**
 * Tests for TotpGenerator validation (C4 fix).
 */
class TotpGeneratorTest {

    private val validSecret = ByteArray(20) { it.toByte() }

    @Test
    fun generate_validInputs_returns6DigitCode() {
        val code = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA1")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_validInputs8Digits_returns8DigitCode() {
        val code = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 8, algorithm = "SHA1")
        assertEquals(8, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_sha256_works() {
        val code = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA256")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_sha512_works() {
        val code = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA512")
        assertEquals(6, code.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_emptySecret_throws() {
        TotpGenerator.generate(ByteArray(0), time = 1000L, step = 30, digits = 6, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_stepZero_throws() {
        TotpGenerator.generate(validSecret, time = 1000L, step = 0, digits = 6, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_stepNegative_throws() {
        TotpGenerator.generate(validSecret, time = 1000L, step = -1, digits = 6, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_digits5_throws() {
        TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 5, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_digits9_throws() {
        TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 9, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_unsupportedAlgorithm_throws() {
        TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "MD5")
    }

    @Test
    fun generate_lowercaseAlgorithm_normalizesAndWorks() {
        // lowercase should be normalized to uppercase
        val code = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "sha1")
        assertEquals(6, code.length)
    }

    @Test
    fun generateHotp_validInputs_returns6DigitCode() {
        val code = TotpGenerator.generateHotp(validSecret, counter = 1L, digits = 6, algorithm = "SHA1")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateHotp_emptySecret_throws() {
        TotpGenerator.generateHotp(ByteArray(0), counter = 1L, digits = 6, algorithm = "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateHotp_unsupportedAlgorithm_throws() {
        TotpGenerator.generateHotp(validSecret, counter = 1L, digits = 6, algorithm = "MD5")
    }

    @Test
    fun remainingSeconds_validStep_returnsPositive() {
        val remaining = TotpGenerator.remainingSeconds(step = 30, time = 1005L)
        assertTrue(remaining > 0)
        assertTrue(remaining <= 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun remainingSeconds_stepZero_throws() {
        TotpGenerator.remainingSeconds(step = 0, time = 1000L)
    }

    @Test
    fun generate_sameInputs_sameOutput() {
        val code1 = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA1")
        val code2 = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA1")
        assertEquals(code1, code2)
    }

    @Test
    fun generate_differentTime_differentOutput() {
        val code1 = TotpGenerator.generate(validSecret, time = 1000L, step = 30, digits = 6, algorithm = "SHA1")
        val code2 = TotpGenerator.generate(validSecret, time = 1031L, step = 30, digits = 6, algorithm = "SHA1")
        assertNotEquals(code1, code2)
    }

    // ------------------------------------------------------------------
    // RFC 4226 (HOTP) official test vectors
    // Secret: "12345678901234567890" (ASCII) -> Base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ
    // Algorithm: SHA1, Digits: 6
    // ------------------------------------------------------------------
    @Test
    fun hotp_rfc4226_vectors_all10_counters() {
        val secret = Base32Codec.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        expected.forEachIndexed { counter, code ->
            assertEquals(
                "HOTP RFC 4226 counter $counter",
                code,
                TotpGenerator.generateHotp(secret, counter = counter.toLong(), digits = 6, algorithm = "SHA1"),
            )
        }
    }

    // ------------------------------------------------------------------
    // RFC 6238 (TOTP) official test vectors
    // Period: 30, Digits: 8
    // ------------------------------------------------------------------
    @Test
    fun totp_rfc6238_sha1_vectors() {
        val secret = Base32Codec.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        vectors.forEach { (time, code) ->
            assertEquals(
                "TOTP RFC 6238 SHA1 t=$time",
                code,
                TotpGenerator.generate(secret, time = time, step = 30, digits = 8, algorithm = "SHA1"),
            )
        }
    }

    @Test
    fun totp_rfc6238_sha256_vectors() {
        // "12345678901234567890123456789012" (ASCII, 32 bytes)
        val secret = "12345678901234567890123456789012".toByteArray(Charsets.US_ASCII)
        val vectors = listOf(
            59L to "46119246",
            1111111109L to "68084774",
            1111111111L to "67062674",
            1234567890L to "91819424",
            2000000000L to "90698825",
            20000000000L to "77737706",
        )
        vectors.forEach { (time, code) ->
            assertEquals(
                "TOTP RFC 6238 SHA256 t=$time",
                code,
                TotpGenerator.generate(secret, time = time, step = 30, digits = 8, algorithm = "SHA256"),
            )
        }
    }

    @Test
    fun totp_rfc6238_sha512_vectors() {
        // "1234567890123456789012345678901234567890123456789012345678901234" (ASCII, 64 bytes)
        val secret = "1234567890123456789012345678901234567890123456789012345678901234"
            .toByteArray(Charsets.US_ASCII)
        val vectors = listOf(
            59L to "90693936",
            1111111109L to "25091201",
            1111111111L to "99943326",
            1234567890L to "93441116",
            2000000000L to "38618901",
            20000000000L to "47863826",
        )
        vectors.forEach { (time, code) ->
            assertEquals(
                "TOTP RFC 6238 SHA512 t=$time",
                code,
                TotpGenerator.generate(secret, time = time, step = 30, digits = 8, algorithm = "SHA512"),
            )
        }
    }

    @Test
    fun totp_rfc6238_6digits() {
        val secret = Base32Codec.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        // 6-digit codes are the last 6 digits of the 8-digit RFC 6238 SHA1 vectors
        val vectors = listOf(
            59L to "287082",
            1111111109L to "081804",
            1111111111L to "050471",
            1234567890L to "005924",
            2000000000L to "279037",
            20000000000L to "353130",
        )
        vectors.forEach { (time, code) ->
            assertEquals(
                "TOTP RFC 6238 SHA1 6-digit t=$time",
                code,
                TotpGenerator.generate(secret, time = time, step = 30, digits = 6, algorithm = "SHA1"),
            )
        }
    }
}
