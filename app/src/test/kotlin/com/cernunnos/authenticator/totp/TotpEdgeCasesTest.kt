package com.cernunnos.authenticator.totp

import org.junit.Test
import org.junit.Assert.*

/**
 * Edge-case tests for TOTP/HOTP generation at boundary conditions.
 */
class TotpEdgeCasesTest {

    @Test
    fun generate_atTimeZero_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, 0L, 30, 6, "SHA1")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_atMaxPeriod_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, Long.MAX_VALUE, 30, 6, "SHA1")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_period1_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 1, 6, "SHA1")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_period60_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 60, 6, "SHA1")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_digits6_returns6Digits() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 30, 6, "SHA1")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_digits8_returns8Digits() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 30, 8, "SHA1")
        assertEquals(8, code.length)
    }

    @Test
    fun generate_sha256_returnsValidCode() {
        val secret = ByteArray(32) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 30, 6, "SHA256")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_sha512_returnsValidCode() {
        val secret = ByteArray(64) { it.toByte() }
        val code = TotpGenerator.generate(secret, 123456L, 30, 6, "SHA512")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_sameTimeSameSecret_returnsSameCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generate(secret, 123456L, 30, 6, "SHA1")
        val code2 = TotpGenerator.generate(secret, 123456L, 30, 6, "SHA1")
        assertEquals(code1, code2)
    }

    @Test
    fun generate_differentTimes_mayReturnDifferentCodes() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generate(secret, 0L, 30, 6, "SHA1")
        val code2 = TotpGenerator.generate(secret, 30L, 30, 6, "SHA1")
        // Codes at different time steps are likely different (not guaranteed, but very likely)
        // We just verify both are valid 6-digit codes
        assertEquals(6, code1.length)
        assertEquals(6, code2.length)
    }

    @Test
    fun generate_hotp_counter0_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateHotp(secret, 0L, 6, "SHA1")
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun generate_hotp_largeCounter_returnsValidCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateHotp(secret, Long.MAX_VALUE, 6, "SHA1")
        assertEquals(6, code.length)
    }

    @Test
    fun generate_hotp_sameCounter_returnsSameCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generateHotp(secret, 42L, 6, "SHA1")
        val code2 = TotpGenerator.generateHotp(secret, 42L, 6, "SHA1")
        assertEquals(code1, code2)
    }

    @Test
    fun generate_hotp_differentCounters_mayReturnDifferentCodes() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generateHotp(secret, 0L, 6, "SHA1")
        val code2 = TotpGenerator.generateHotp(secret, 1L, 6, "SHA1")
        assertEquals(6, code1.length)
        assertEquals(6, code2.length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generate_emptySecret_throws() {
        TotpGenerator.generate(ByteArray(0), 123456L, 30, 6, "SHA1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateHotp_emptySecret_throws() {
        TotpGenerator.generateHotp(ByteArray(0), 0L, 6, "SHA1")
    }

    @Test
    fun generate_rfc6238TestVector_sha1_period30_digits8() {
        // RFC 6238 Appendix B test vector
        // Secret: "12345678901234567890" (ASCII)
        // Time: 59 seconds → T = 0x0000000000000001
        // Expected: 94287082
        val secret = "12345678901234567890".toByteArray()
        val code = TotpGenerator.generate(secret, 59L, 30, 8, "SHA1")
        assertEquals("94287082", code)
    }

    @Test
    fun generate_rfc6238TestVector_sha256_period30_digits8() {
        // RFC 6238 Appendix B test vector for SHA256
        // Secret: "12345678901234567890123456789012" (ASCII, 32 bytes)
        // Time: 59 seconds
        // Expected: 46119246
        val secret = "12345678901234567890123456789012".toByteArray()
        val code = TotpGenerator.generate(secret, 59L, 30, 8, "SHA256")
        assertEquals("46119246", code)
    }

    @Test
    fun generate_rfc6238TestVector_sha512_period30_digits8() {
        // RFC 6238 Appendix B test vector for SHA512
        // Secret: "1234567890123456789012345678901234567890123456789012345678901234" (64 bytes)
        // Time: 59 seconds
        // Expected: 90693936
        val secret = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()
        val code = TotpGenerator.generate(secret, 59L, 30, 8, "SHA512")
        assertEquals("90693936", code)
    }

    @Test
    fun generate_rfc4226TestVector_hotp_counter0() {
        // RFC 4226 Appendix D test vector
        // Secret: "12345678901234567890" (ASCII)
        // Counter: 0
        // Expected: 755224
        val secret = "12345678901234567890".toByteArray()
        val code = TotpGenerator.generateHotp(secret, 0L, 6, "SHA1")
        assertEquals("755224", code)
    }

    @Test
    fun generate_rfc4226TestVector_hotp_counter1() {
        // RFC 4226 Appendix D test vector
        // Counter: 1
        // Expected: 287082
        val secret = "12345678901234567890".toByteArray()
        val code = TotpGenerator.generateHotp(secret, 1L, 6, "SHA1")
        assertEquals("287082", code)
    }
}
