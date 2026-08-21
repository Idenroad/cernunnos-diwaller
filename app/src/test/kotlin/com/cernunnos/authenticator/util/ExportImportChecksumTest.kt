package com.cernunnos.authenticator.util

import org.junit.Test
import org.junit.Assert.*
import java.lang.IllegalArgumentException

/**
 * Tests for ExportImport checksum (P3-1 fix) and legacy compatibility.
 */
class ExportImportChecksumTest {

    private val passphrase = "testPassphrase123!".toCharArray()
    private val validEntries = listOf(
        com.cernunnos.authenticator.data.model.TotpEntry(
            id = "test1",
            issuer = "TestIssuer",
            label = "test@example.com",
            secret = ByteArray(20) { it.toByte() },
            algorithm = "SHA1",
            digits = 6,
            period = 30,
        )
    )

    @Test
    fun export_v1Format_has5Parts() {
        val exported = ExportImport.export(validEntries, String(passphrase))
        val parts = exported.split(":")
        assertEquals(5, parts.size)
        assertEquals("v1", parts[0])
    }

    @Test
    fun export_import_roundTrip_returnsSameEntries() {
        val exported = ExportImport.export(validEntries, String(passphrase))
        val imported = ExportImport.import(exported, String(passphrase))
        assertEquals(1, imported.size)
        assertEquals("test1", imported[0].id)
        assertEquals("TestIssuer", imported[0].issuer)
        assertEquals("test@example.com", imported[0].label)
        assertEquals(6, imported[0].digits)
        assertEquals(30, imported[0].period)
        assertArrayEquals(validEntries[0].secret, imported[0].secret)
    }

    @Test
    fun import_corruptedChecksum_throws() {
        val exported = ExportImport.export(validEntries, String(passphrase))
        val parts = exported.split(":").toMutableList()
        // Tamper with checksum
        parts[1] = "AAAA" + parts[1].substring(4)
        val tampered = parts.joinToString(":")

        try {
            ExportImport.import(tampered, String(passphrase))
            fail("Expected checksum mismatch exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("corrupted") || e.message!!.contains("checksum"))
        }
    }

    @Test
    fun import_truncatedData_throws() {
        val exported = ExportImport.export(validEntries, String(passphrase))
        // Truncate the ciphertext
        val truncated = exported.substring(0, exported.length - 10)
        try {
            ExportImport.import(truncated, String(passphrase))
            fail("Expected exception for truncated data")
        } catch (e: Exception) {
            // Should fail with checksum mismatch or decryption error
            assertTrue(true)
        }
    }

    @Test
    fun import_wrongPassphrase_throws() {
        val exported = ExportImport.export(validEntries, String(passphrase))
        try {
            ExportImport.import(exported, "wrongPassphrase!")
            fail("Expected decryption failure")
        } catch (e: Exception) {
            // GCM tag verification should fail
            assertTrue(true)
        }
    }

    @Test
    fun import_invalidFormat_throws() {
        try {
            ExportImport.import("invalid:data", String(passphrase))
            fail("Expected invalid format exception")
        } catch (e: Exception) {
            // Could be IllegalArgumentException or IllegalStateException depending on path
            assertTrue(e.message!!.contains("Invalid export format") || e.message!!.contains("expected"))
        }
    }

    @Test
    fun import_emptyData_throws() {
        try {
            ExportImport.import("", String(passphrase))
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(true)
        }
    }

    @Test
    fun export_multipleEntries_roundTripPreservesAll() {
        val entries = listOf(
            com.cernunnos.authenticator.data.model.TotpEntry(
                id = "e1", issuer = "A", label = "a@x.com",
                secret = ByteArray(20), algorithm = "SHA1", digits = 6, period = 30,
            ),
            com.cernunnos.authenticator.data.model.TotpEntry(
                id = "e2", issuer = "B", label = "b@x.com",
                secret = ByteArray(32) { it.toByte() }, algorithm = "SHA256", digits = 8, period = 60,
            ),
        )
        val exported = ExportImport.export(entries, String(passphrase))
        val imported = ExportImport.import(exported, String(passphrase))
        assertEquals(2, imported.size)
        assertEquals("e1", imported[0].id)
        assertEquals("e2", imported[1].id)
        assertEquals(8, imported[1].digits)
        assertEquals(60, imported[1].period)
    }
}
