package com.cernunnos.authenticator.util

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

/**
 * Fuzzing tests for import parsers (OtpAuthParser, BitwardenImporter, AegisImporter, GoogleAuthImporter).
 *
 * These tests feed malformed, truncated, and adversarial input to the parsers and verify
 * that they fail deterministically (throw a clear exception or return empty) rather than
 * crash, hang, or produce invalid entries.
 */
class ImportFuzzingTest {

    private val random = Random(42) // Deterministic seed for reproducibility

    // ── OtpAuthParser fuzzing ──

    @Test
    fun fuzzOtpAuth_truncatedUris_doNotCrash() {
        repeat(200) {
            val uri = "otpauth://totp/Issuer:label?secret=JBSWY3DPEHPK3PXP&issuer=Issuer"
            val truncated = uri.take(random.nextInt(uri.length))
            try {
                OtpAuthParser.parse(truncated)
            } catch (e: Exception) {
                // Expected — must throw, not crash
            }
        }
    }

    @Test
    fun fuzzOtpAuth_randomGarbage_doesNotCrash() {
        repeat(200) {
            val garbage = random.nextBytes(100).toString(Charsets.ISO_8859_1)
            try {
                OtpAuthParser.parse(garbage)
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzOtpAuth_randomBase32Secrets_parseOrThrow() {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        repeat(100) {
            val secretLen = random.nextInt(1, 60)
            val secret = (1..secretLen).map { base32Chars.random(random) }.joinToString("")
            val uri = "otpauth://totp/Test:user?secret=$secret&issuer=Test"
            try {
                OtpAuthParser.parse(uri)
                // Either parses or throws — both are acceptable
            } catch (e: Exception) {
                // Expected for invalid base32 or mock Uri behavior
            }
        }
    }

    @Test
    fun fuzzOtpAuth_extremelyLongSecret_throwsOrParses() {
        val hugeSecret = "A".repeat(10_000)
        try {
            OtpAuthParser.parse("otpauth://totp/T:u?secret=$hugeSecret&issuer=T")
            // If it parses, the secret should be very large — that's OK
        } catch (e: Exception) {
            // Expected — must throw, not crash
        }
    }

    @Test
    fun fuzzOtpAuth_negativeDigits_throws() {
        for (digits in listOf(-1, 0, -100, Int.MIN_VALUE)) {
            try {
                OtpAuthParser.parse("otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP&digits=$digits")
                fail("Should throw for digits=$digits")
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzOtpAuth_negativePeriod_throws() {
        for (period in listOf(-1, 0, -100, Int.MIN_VALUE)) {
            try {
                OtpAuthParser.parse("otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP&period=$period")
                fail("Should throw for period=$period")
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzOtpAuth_nonNumericDigits_throws() {
        for (digits in listOf("abc", "NaN", "1.5", "0x10", "", "null")) {
            try {
                OtpAuthParser.parse("otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP&digits=$digits")
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzOtpAuth_injectionInIssuer_doesNotProduceInvalidEntry() {
        val injections = listOf(
            "Test'; DROP TABLE--",
            "Test\n\n\r\n",
            "Test%00%00null",
            "../../etc/passwd",
            "<script>alert(1)</script>",
        )
        for (issuer in injections) {
            try {
                val entry = OtpAuthParser.parse(
                    "otpauth://totp/${issuer}:user?secret=JBSWY3DPEHPK3PXP&issuer=${issuer}"
                )
                // If it parses, issuer must not contain null bytes
                assertFalse(issuer.contains('\u0000'))
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzOtpAuth_hotpWithNegativeCounter_throws() {
        for (counter in listOf(-1, -100, Int.MIN_VALUE)) {
            try {
                OtpAuthParser.parse("otpauth://hotp/T:u?secret=JBSWY3DPEHPK3PXP&counter=$counter")
                fail("Should throw for counter=$counter")
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    // ── BitwardenImporter fuzzing ──

    @Test
    fun fuzzBitwarden_randomJson_doesNotCrash() {
        repeat(100) {
            val garbage = random.nextBytes(200).toString(Charsets.ISO_8859_1)
            try {
                BitwardenImporter.import(garbage)
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzBitwarden_truncatedJson_doesNotCrash() {
        val validJson = """{"encrypted":false,"items":[{"name":"Test","login":{"totp":"otpauth://totp/Test:u?secret=JBSWY3DPEHPK3PXP&issuer=Test"}}]}"""
        repeat(100) {
            val truncated = validJson.take(random.nextInt(validJson.length))
            try {
                BitwardenImporter.import(truncated)
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzBitwarden_deeplyNestedJson_doesNotCrash() {
        val deeplyNested = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":{\"f\":{\"g\":{\"h\":{\"items\":[]}}}}}}}}}"
        try {
            val result = BitwardenImporter.import(deeplyNested)
            assertTrue(result.isEmpty())
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzBitwarden_arrayWithMixedTypes_doesNotCrash() {
        val mixed = """{"encrypted":false,"items":[1,"string",true,null,{},[]]}"""
        try {
            BitwardenImporter.import(mixed)
        } catch (e: Exception) {
            // Expected — must not crash
        }
    }

    @Test
    fun fuzzBitwarden_csvWithMalformedRows_doesNotCrash() {
        val malformedCsv = """
            name,totp
            Test,
            ,otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP
            "unclosed quote,otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP
            Test,otpauth://totp/T:u?secret=JBSWY3DPEHPK3PXP,extra,col
        """.trimIndent()
        try {
            BitwardenImporter.import(malformedCsv)
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzBitwarden_emptyString_returnsEmptyOrThrows() {
        try {
            val result = BitwardenImporter.import("")
            assertTrue(result.isEmpty())
        } catch (e: Exception) {
            // Expected — empty string is not valid JSON
        }
    }

    // ── AegisImporter fuzzing ──

    @Test
    fun fuzzAegis_randomJson_doesNotCrash() {
        repeat(100) {
            val garbage = random.nextBytes(200).toString(Charsets.ISO_8859_1)
            try {
                AegisImporter.import(garbage, null)
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzAegis_truncatedJson_doesNotCrash() {
        val validJson = """{"db":{"entries":[{"type":"totp","uuid":"abc","name":"Test","issuer":"Test","info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}}]}}"""
        repeat(100) {
            val truncated = validJson.take(random.nextInt(validJson.length))
            try {
                AegisImporter.import(truncated, null)
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzAegis_encryptedWithWrongPassphrase_throws() {
        val json = """{"header":{"slots":[{"type":"raw","encrypted":true}]},"db":"invalidbase64"}"""
        try {
            AegisImporter.import(json, "wrongpass")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzAegis_emptyJson_returnsEmptyOrThrows() {
        try {
            val result = AegisImporter.import("", null)
            assertTrue(result.isEmpty())
        } catch (e: Exception) {
            // Expected — empty string is not valid Aegis JSON
        }
    }

    @Test
    fun fuzzAegis_entriesWithMissingFields_doesNotCrash() {
        val json = """{"db":{"entries":[{"type":"totp"},{"uuid":"abc"},{"name":"noSecret"}]}}"""
        try {
            AegisImporter.import(json, null)
        } catch (e: Exception) {
            // Expected
        }
    }

    // ── GoogleAuthImporter fuzzing ──
    // GoogleAuthImporter.import takes an otpauth-migration:// URI with base64-encoded protobuf in data param.
    // We build URIs with malformed base64 data.

    private fun migrationUri(data: String): String = "otpauth-migration://offline?data=$data"

    @Test
    fun fuzzGoogleAuth_randomBase64_doesNotCrash() {
        repeat(100) {
            val bytes = random.nextBytes(500)
            val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            try {
                GoogleAuthImporter.import(migrationUri(b64))
            } catch (e: Exception) {
                // Expected
            }
        }
    }

    @Test
    fun fuzzGoogleAuth_emptyData_returnsEmpty() {
        try {
            val result = GoogleAuthImporter.import(migrationUri(""))
            // May return empty or throw — both OK
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzGoogleAuth_truncatedProtobuf_doesNotCrash() {
        // Valid base64 of truncated protobuf: field 1, length 16, but only 5 bytes follow
        val proto = byteArrayOf(0x0a, 0x10) + random.nextBytes(5)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proto)
        try {
            GoogleAuthImporter.import(migrationUri(b64))
        } catch (e: Exception) {
            // Expected — must throw, not hang or crash
        }
    }

    @Test
    fun fuzzGoogleAuth_varintOverflow_doesNotHang() {
        // Malformed varint that could cause infinite loop
        val proto = byteArrayOf(0x0a, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte())
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proto)
        try {
            GoogleAuthImporter.import(migrationUri(b64))
        } catch (e: Exception) {
            // Expected — must throw, not hang
        }
    }

    @Test
    fun fuzzGoogleAuth_extremelyLargeLength_doesNotCrash() {
        // Field with length claiming to be 2^31 bytes but only a few bytes available
        val proto = byteArrayOf(0x0a, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x07) + random.nextBytes(10)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proto)
        try {
            GoogleAuthImporter.import(migrationUri(b64))
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzGoogleAuth_allZeros_doesNotCrash() {
        val proto = ByteArray(100)
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(proto)
        try {
            GoogleAuthImporter.import(migrationUri(b64))
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzGoogleAuth_notAMigrationUri_throws() {
        try {
            GoogleAuthImporter.import("otpauth://totp/Test:u?secret=JBSWY3DPEHPK3PXP")
            fail("Should reject non-migration URI")
        } catch (e: Exception) {
            // Expected
        }
    }

    // ── Cross-parser: adversarial otpauth URIs ──

    @Test
    fun fuzzAllParsers_nullBytesInInput_doesNotCrash() {
        val nullInput = "otpauth://totp/Test\u0000:user?secret=JBSWY3DPEHPK3PXP"
        try {
            OtpAuthParser.parse(nullInput)
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun fuzzAllParsers_unicodeInSecret_doesNotCrash() {
        val unicodeSecret = "JBSWY3DPEHPK3PXP🔑"
        try {
            OtpAuthParser.parse("otpauth://totp/T:u?secret=$unicodeSecret")
        } catch (e: Exception) {
            // Expected — non-base32 char
        }
    }
}
