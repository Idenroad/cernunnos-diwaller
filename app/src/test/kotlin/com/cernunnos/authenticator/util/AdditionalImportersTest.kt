package com.cernunnos.authenticator.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the additional importers (2FAS, Authy, Microsoft, FreeOTP, andOTP, Raivo, LastPass, Steam, PlainText).
 */
class AdditionalImportersTest {

    @Test
    fun plainTextImporter_oneUri_returnsEntry() {
        val text = "otpauth://totp/GitHub:user@github.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"
        val entries = PlainTextImporter.import(text)
        assertEquals(1, entries.size)
        assertEquals("GitHub", entries[0].issuer)
    }

    @Test
    fun plainTextImporter_multipleUris_returnsAllEntries() {
        val text = """
            otpauth://totp/GitHub:user@github.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
            otpauth://totp/Amazon:user@amazon.com?secret=JBSWY3DPEHPK3PXP&issuer=Amazon
            otpauth://totp/Dropbox:user@dropbox.com?secret=JBSWY3DPEHPK3PXP&issuer=Dropbox
        """.trimIndent()
        val entries = PlainTextImporter.import(text)
        assertEquals(3, entries.size)
    }

    @Test
    fun plainTextImporter_withComments_ignoresCommentLines() {
        val text = """
            # This is a comment
            otpauth://totp/GitHub:user@github.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
            # Another comment
            otpauth://totp/Amazon:user@amazon.com?secret=JBSWY3DPEHPK3PXP&issuer=Amazon
        """.trimIndent()
        val entries = PlainTextImporter.import(text)
        assertEquals(2, entries.size)
    }

    @Test
    fun plainTextImporter_emptyLines_ignored() {
        val text = """

        otpauth://totp/GitHub:user@github.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub

        """.trimIndent()
        val entries = PlainTextImporter.import(text)
        assertEquals(1, entries.size)
    }

    @Test
    fun plainTextImporter_invalidLines_skipped() {
        val text = """
            not a valid line
            otpauth://totp/GitHub:user@github.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub
            also invalid
        """.trimIndent()
        val entries = PlainTextImporter.import(text)
        assertEquals(1, entries.size)
    }

    @Test
    fun plainTextImporter_emptyText_returnsEmpty() {
        val entries = PlainTextImporter.import("")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun twoFasImporter_validJson_returnsEntries() {
        val json = """
        {
            "schemaVersion": 1,
            "services": [
                {
                    "otp": {
                        "issuer": "GitHub",
                        "account": "user@github.com",
                        "secret": "JBSWY3DPEHPK3PXP",
                        "algorithm": "SHA1",
                        "digits": 6,
                        "period": 30,
                        "otpType": "TOTP"
                    },
                    "name": "GitHub"
                },
                {
                    "otp": {
                        "issuer": "Amazon",
                        "account": "user@amazon.com",
                        "secret": "JBSWY3DPEHPK3PXP",
                        "algorithm": "SHA256",
                        "digits": 8,
                        "period": 30,
                        "otpType": "TOTP"
                    },
                    "name": "Amazon"
                }
            ],
            "groups": []
        }
        """.trimIndent()
        val entries = TwoFasImporter.import(json)
        assertEquals(2, entries.size)
        assertEquals("GitHub", entries[0].issuer)
        assertEquals("user@github.com", entries[0].label)
        assertEquals("SHA1", entries[0].algorithm)
        assertEquals(6, entries[0].digits)
        assertEquals("Amazon", entries[1].issuer)
        assertEquals("SHA256", entries[1].algorithm)
        assertEquals(8, entries[1].digits)
    }

    @Test
    fun twoFasImporter_emptyServices_returnsEmpty() {
        val json = """{"schemaVersion":1,"services":[],"groups":[]}"""
        val entries = TwoFasImporter.import(json)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun twoFasImporter_invalidSecret_skipped() {
        val json = """
        {
            "schemaVersion": 1,
            "services": [
                {
                    "otp": {
                        "issuer": "Valid",
                        "account": "user",
                        "secret": "JBSWY3DPEHPK3PXP",
                        "otpType": "TOTP"
                    },
                    "name": "Valid"
                },
                {
                    "otp": {
                        "issuer": "Invalid",
                        "account": "user",
                        "secret": "!!!invalidbase32!!!",
                        "otpType": "TOTP"
                    },
                    "name": "Invalid"
                }
            ]
        }
        """.trimIndent()
        val entries = TwoFasImporter.import(json)
        assertEquals(1, entries.size)
        assertEquals("Valid", entries[0].issuer)
    }

    @Test
    fun andOtpImporter_validJson_returnsEntries() {
        val json = """
        [
            {
                "secret": "JBSWY3DPEHPK3PXP",
                "issuer": "GitHub",
                "label": "user@github.com",
                "digits": 6,
                "type": "TOTP",
                "algorithm": "SHA1",
                "period": 30
            },
            {
                "secret": "JBSWY3DPEHPK3PXP",
                "issuer": "Amazon",
                "label": "user@amazon.com",
                "digits": 8,
                "type": "HOTP",
                "algorithm": "SHA256",
                "period": 30,
                "counter": 5
            }
        ]
        """.trimIndent()
        val entries = AndOtpImporter.import(json)
        assertEquals(2, entries.size)
        assertEquals("GitHub", entries[0].issuer)
        assertEquals("totp", entries[0].type)
        assertEquals("Amazon", entries[1].issuer)
        assertEquals("hotp", entries[1].type)
        assertEquals(5L, entries[1].counter)
        assertEquals(8, entries[1].digits)
    }

    @Test
    fun raivoOtpImporter_validJson_returnsEntries() {
        val json = """
        {
            "app": "raivo-otp",
            "version": 1,
            "entries": [
                {
                    "issuer": "GitHub",
                    "account": "user@github.com",
                    "secret": "JBSWY3DPEHPK3PXP",
                    "algorithm": "sha1",
                    "digits": 6,
                    "period": 30,
                    "type": "totp"
                }
            ]
        }
        """.trimIndent()
        val entries = RaivoOtpImporter.import(json)
        assertEquals(1, entries.size)
        assertEquals("GitHub", entries[0].issuer)
        assertEquals("user@github.com", entries[0].label)
        assertEquals("SHA1", entries[0].algorithm)
    }

    @Test
    fun lastPassImporter_validCsv_returnsEntries() {
        val csv = """
            issuer,label,secret,digits,type,algorithm,period
            GitHub,user@github.com,JBSWY3DPEHPK3PXP,6,totp,SHA1,30
            Amazon,user@amazon.com,JBSWY3DPEHPK3PXP,8,totp,SHA256,30
        """.trimIndent()
        val entries = LastPassImporter.import(csv)
        assertEquals(2, entries.size)
        assertEquals("GitHub", entries[0].issuer)
        assertEquals("Amazon", entries[1].issuer)
        assertEquals(8, entries[1].digits)
    }

    @Test
    fun lastPassImporter_simpleFormat_returnsEntries() {
        val csv = """
            name,username,secret
            GitHub,user@github.com,JBSWY3DPEHPK3PXP
        """.trimIndent()
        val entries = LastPassImporter.import(csv)
        assertEquals(1, entries.size)
        assertEquals("GitHub", entries[0].issuer)
    }

    @Test
    fun lastPassImporter_emptyCsv_returnsEmpty() {
        val entries = LastPassImporter.import("")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun steamImporter_validJson_returnsEntry() {
        // shared_secret is base64-encoded
        val secretBytes = ByteArray(20) { it.toByte() }
        val secretB64 = java.util.Base64.getEncoder().encodeToString(secretBytes)
        val json = """
        {
            "shared_secret": "$secretB64",
            "account_name": "steamuser",
            "device_id": "android:abc123"
        }
        """.trimIndent()
        val entries = SteamImporter.import(json)
        assertEquals(1, entries.size)
        assertEquals("Steam", entries[0].issuer)
        assertEquals("steamuser", entries[0].label)
        assertEquals(5, entries[0].digits)
    }

    @Test
    fun steamImporter_missingSecret_returnsEmpty() {
        val json = """{"account_name": "steamuser"}"""
        val entries = SteamImporter.import(json)
        assertTrue(entries.isEmpty())
    }
}
