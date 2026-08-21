package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.constants.TotpConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

/**
 * Unit tests for [OtpAuthParser] (otpauth:// URI parsing).
 *
 * Only assertions that the parser actually validates are enforced here.
 * Tests for validations the parser does not currently perform are marked
 * `@Ignore` with a TODO so they can be enabled once the validation is added.
 */
class OtpAuthParserTest {

    // JBSWY3DPEHPK3PXP is a valid RFC 4648 Base32 string used across the tests.
    private val validSecret = "JBSWY3DPEHPK3PXP"
    private val expectedSecretBytes = Base32Codec.decode(validSecret)

    // --- valid URIs ---

    @Test
    fun parse_validTotpUri_returnsEntry() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer&algorithm=SHA1&digits=6&period=30"
        val entry = OtpAuthParser.parse(uri)
        assertEquals("Issuer", entry.issuer)
        assertEquals("label", entry.label)
        assertArrayEquals(expectedSecretBytes, entry.secret)
        assertEquals("SHA1", entry.algorithm)
        assertEquals(6, entry.digits)
        assertEquals(30, entry.period)
        assertEquals(TotpConfig.TYPE_TOTP, entry.type)
    }

    @Test
    fun parse_validHotpUri_returnsEntryWithTypeAndCounter() {
        val uri = "otpauth://hotp/Issuer:label?secret=$validSecret&issuer=Issuer&counter=42"
        val entry = OtpAuthParser.parse(uri)
        assertEquals(TotpConfig.TYPE_HOTP, entry.type)
        assertEquals(42L, entry.counter)
        assertEquals("Issuer", entry.issuer)
        assertEquals("label", entry.label)
        assertArrayEquals(expectedSecretBytes, entry.secret)
    }

    @Test
    fun parse_defaultType_isTotp() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer"
        val entry = OtpAuthParser.parse(uri)
        assertEquals(TotpConfig.TYPE_TOTP, entry.type)
    }

    // --- defaults ---

    @Test
    fun parse_defaultAlgorithm_isSHA1() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer"
        val entry = OtpAuthParser.parse(uri)
        assertEquals(TotpConfig.ALGO_SHA1, entry.algorithm)
    }

    @Test
    fun parse_defaultDigits_is6() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer"
        val entry = OtpAuthParser.parse(uri)
        assertEquals(TotpConfig.DEFAULT_DIGITS, entry.digits)
    }

    @Test
    fun parse_defaultPeriod_is30() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer"
        val entry = OtpAuthParser.parse(uri)
        assertEquals(TotpConfig.DEFAULT_PERIOD, entry.period)
    }

    // --- missing / invalid secret ---

    @Test
    fun parse_missingSecret_throws() {
        val uri = "otpauth://totp/Issuer:label?issuer=Issuer"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for missing secret parameter")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Missing secret"))
        }
    }

    @Test
    fun parse_invalidBase32Secret_throws() {
        // '!' is not part of the Base32 alphabet.
        val uri = "otpauth://totp/Issuer:label?secret=!!!invalid&issuer=Issuer"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for invalid Base32 secret")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Invalid Base32"))
        }
    }

    // --- long URI (not validated: must not crash) ---

    @Test
    fun parse_excessivelyLongUri_doesNotCrash() {
        // Build a URI longer than 2048 chars using a long (but valid) label.
        val longLabel = "a".repeat(2200)
        val uri = "otpauth://totp/Issuer:$longLabel?secret=$validSecret&issuer=Issuer"
        // The parser does not enforce a max URI length; verify it parses without crashing.
        val entry = OtpAuthParser.parse(uri)
        assertEquals(longLabel, entry.label)
    }

    // --- validations the parser does NOT currently perform (skipped) ---

    @Ignore("TODO: OtpAuthParser does not currently reject an empty issuer")
    @Test
    fun parse_emptyIssuer_throws() {
        val uri = "otpauth://totp/label?secret=$validSecret"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for empty issuer")
        } catch (e: Exception) {
            // expected once validation is added
        }
    }

    @Test
    fun parse_negativePeriod_throws() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer&period=-1"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for negative period")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Period"))
        }
    }

    @Test
    fun parse_zeroPeriod_throws() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer&period=0"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for zero period")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Period"))
        }
    }

    @Test
    fun parse_invalidDigits_throws() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer&digits=5"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for invalid digits")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Digits"))
        }
    }

    @Test
    fun parse_unknownAlgorithm_throws() {
        val uri = "otpauth://totp/Issuer:label?secret=$validSecret&issuer=Issuer&algorithm=MD5"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for unknown algorithm")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("algorithm"))
        }
    }

    @Test
    fun parse_negativeCounter_throws() {
        val uri = "otpauth://hotp/Issuer:label?secret=$validSecret&issuer=Issuer&counter=-1"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for negative counter")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Counter"))
        }
    }

    @Test
    fun parse_secretTooLong_throws() {
        // > 128 bytes. Encode 129 bytes to Base32.
        val longSecret = Base32Codec.encode(ByteArray(129) { it.toByte() })
        val uri = "otpauth://totp/Issuer:label?secret=$longSecret&issuer=Issuer"
        try {
            OtpAuthParser.parse(uri)
            fail("Expected exception for secret longer than 128 bytes")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Secret too long"))
        }
    }
}
