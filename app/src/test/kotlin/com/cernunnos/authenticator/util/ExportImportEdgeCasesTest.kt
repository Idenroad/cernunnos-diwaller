package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Test
import org.junit.Assert.*
import java.util.Base64 as JBase64

/**
 * Edge-case tests for ExportImport (in addition to ExportImportChecksumTest).
 */
class ExportImportEdgeCasesTest {

    private val passphrase = "testPassphrase123!"

    private fun entry(
        id: String = "id1",
        issuer: String = "Issuer",
        label: String = "user@example.com",
        secret: ByteArray = ByteArray(20) { it.toByte() },
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = "totp",
        counter: Long = 0L,
    ) = TotpEntry(id, issuer, label, secret, algorithm, digits, period, type = type, counter = counter)

    @Test
    fun export_emptyList_returnsValidFormat() {
        val exported = ExportImport.export(emptyList(), passphrase)
        val parts = exported.split(":")
        assertEquals(5, parts.size)
        assertEquals("v1", parts[0])
        // Round-trip should yield an empty list, not crash.
        val imported = ExportImport.import(exported, passphrase)
        assertTrue(imported.isEmpty())
    }

    @Test
    fun export_entriesWithSpecialChars_preservesData() {
        val entries = listOf(
            entry(
                id = "sp1",
                issuer = "Café ☕ \"Quotes\"",
                label = "naïve@exämple.com 🎉",
                secret = ByteArray(20) { (it * 7).toByte() },
            )
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(1, imported.size)
        assertEquals("Café ☕ \"Quotes\"", imported[0].issuer)
        assertEquals("naïve@exämple.com 🎉", imported[0].label)
        assertArrayEquals(entries[0].secret, imported[0].secret)
    }

    @Test
    fun export_entriesWithLongSecrets_preservesData() {
        val longSecret = ByteArray(256) { (it % 127).toByte() }
        val entries = listOf(entry(secret = longSecret))
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(1, imported.size)
        assertArrayEquals(longSecret, imported[0].secret)
    }

    @Test
    fun import_v0LegacyFormat_works() {
        // Build a v0 (legacy) export: salt:iv:ciphertext (no version, no checksum).
        val entries = listOf(entry())
        val exportedV1 = ExportImport.export(entries, passphrase)
        val parts = exportedV1.split(":")
        assertEquals(5, parts.size)
        // v0 payload is the last 3 parts (salt:iv:ciphertext).
        val v0Data = parts.subList(2, 5).joinToString(":")
        val imported = ExportImport.import(v0Data, passphrase)
        assertEquals(1, imported.size)
        assertEquals("id1", imported[0].id)
    }

    @Test
    fun import_malformedBase64_throws() {
        // v1 format with non-Base64 characters in the salt field.
        val bad = "v1:checksum!!!:not@base64!:AAAA:BBBB"
        try {
            ExportImport.import(bad, passphrase)
            fail("Expected exception for malformed Base64")
        } catch (e: Exception) {
            // IllegalArgumentException from require() or decoding error
            assertTrue(true)
        }
    }

    @Test
    fun import_invalidJson_throws() {
        // Manually encrypt invalid JSON to produce a valid v1 envelope with bad payload.
        val salt = Argon2id.generateSalt()
        val encrypted = CryptoManager.encrypt("not valid json{{".toByteArray(), passphrase.toCharArray(), salt)
        val b64 = java.util.Base64.getEncoder().withoutPadding()
        val payload = "${b64.encodeToString(salt)}:" +
                "${b64.encodeToString(encrypted.iv)}:" +
                b64.encodeToString(encrypted.ciphertext)
        // Compute checksum the same way ExportImport does (sha256 base64 of payload).
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val checksum = b64.encodeToString(digest)
        val data = "v1:$checksum:$payload"
        try {
            ExportImport.import(data, passphrase)
            fail("Expected exception for invalid JSON")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Invalid JSON") || e.message!!.contains("JSON"))
        }
    }

    @Test
    fun import_tooFewParts_throws() {
        try {
            ExportImport.import("onlyonepart", passphrase)
            fail("Expected exception for too few parts")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Invalid export format") || e.message!!.contains("expected"))
        }
    }

    @Test
    fun import_tooManyParts_throws() {
        try {
            ExportImport.import("a:b:c:d:e:f", passphrase)
            fail("Expected exception for too many parts")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Invalid export format") || e.message!!.contains("expected"))
        }
    }

    @Test
    fun import_emptyString_throws() {
        try {
            ExportImport.import("", passphrase)
            fail("Expected exception for empty string")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun import_whitespaceOnly_throws() {
        try {
            ExportImport.import("   \n\t  ", passphrase)
            fail("Expected exception for whitespace-only string")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun export_thenImport_multipleEntriesWithHOTP_preservesCounter() {
        val entries = listOf(
            entry(id = "h1", type = "hotp", counter = 42L),
            entry(id = "h2", type = "hotp", counter = 999L, secret = ByteArray(20) { it.toByte() }),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(2, imported.size)
        assertEquals("h1", imported[0].id)
        assertEquals("hotp", imported[0].type)
        assertEquals(42L, imported[0].counter)
        assertEquals("h2", imported[1].id)
        assertEquals("hotp", imported[1].type)
        assertEquals(999L, imported[1].counter)
    }

    @Test
    fun export_thenImport_entryWithAllFields_preservesEverything() {
        val entries = listOf(
            entry(
                id = "full1",
                issuer = "FullIssuer",
                label = "full@user.com",
                secret = ByteArray(32) { (it * 3).toByte() },
                algorithm = "SHA256",
                digits = 8,
                period = 60,
                type = "totp",
                counter = 0L,
            )
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(1, imported.size)
        val e = imported[0]
        assertEquals("full1", e.id)
        assertEquals("FullIssuer", e.issuer)
        assertEquals("full@user.com", e.label)
        assertArrayEquals(entries[0].secret, e.secret)
        assertEquals("SHA256", e.algorithm)
        assertEquals(8, e.digits)
        assertEquals(60, e.period)
    }
}
