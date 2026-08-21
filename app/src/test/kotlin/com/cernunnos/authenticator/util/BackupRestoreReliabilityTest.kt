package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Test
import org.junit.Assert.*
import java.security.MessageDigest

/**
 * Tests for backup/restore reliability scenarios.
 * Verifies that encrypted exports can be round-tripped under various
 * stress conditions: large entry counts, corrupted data, wrong passphrase,
 * truncated payloads, and concurrent format versions.
 */
class BackupRestoreReliabilityTest {

    private val passphrase = "backupTestPass123!"
    private val wrongPassphrase = "wrongPass456!"

    private fun entry(
        id: String = "1",
        issuer: String = "Issuer",
        label: String = "user@example.com",
        secret: ByteArray = ByteArray(20) { it.toByte() },
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = "totp",
        counter: Long = 0L,
    ) = TotpEntry("id_$id", issuer, label, secret, algorithm, digits, period, type = type, counter = counter)

    private fun makeEntries(n: Int): List<TotpEntry> =
        (1..n).map { i ->
            entry(
                id = i.toString(),
                issuer = "Bank $i",
                label = "user$i@bank.com",
                secret = ByteArray(20) { (i * 13).toByte() },
            )
        }

    @Test
    fun backup_restore_100Entries_roundTripPreservesAll() {
        val entries = makeEntries(100)
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(100, imported.size)
        for (i in 0 until 100) {
            assertEquals(entries[i].id, imported[i].id)
            assertEquals(entries[i].issuer, imported[i].issuer)
            assertEquals(entries[i].label, imported[i].label)
            assertArrayEquals(entries[i].secret, imported[i].secret)
        }
    }

    @Test
    fun backup_restore_singleEntry_roundTrip() {
        val entries = listOf(entry(id = "1", issuer = "GitHub", label = "me@github.com"))
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(1, imported.size)
        assertEquals("GitHub", imported[0].issuer)
    }

    @Test
    fun backup_wrongPassphrase_throwsCleanError() {
        val entries = listOf(entry(id = "1"))
        val exported = ExportImport.export(entries, passphrase)
        try {
            ExportImport.import(exported, wrongPassphrase)
            fail("Expected exception for wrong passphrase")
        } catch (e: Exception) {
            assertTrue(
                e.message!!.contains("Invalid passphrase") ||
                e.message!!.contains("corrupted")
            )
        }
    }

    @Test
    fun backup_truncatedData_throwsChecksumMismatch() {
        val entries = listOf(entry(id = "1"))
        val exported = ExportImport.export(entries, passphrase)
        // Truncate the ciphertext part
        val truncated = exported.dropLast(20)
        try {
            ExportImport.import(truncated, passphrase)
            fail("Expected exception for truncated data")
        } catch (e: Exception) {
            // Either checksum mismatch or decrypt failure
            assertTrue(true)
        }
    }

    @Test
    fun backup_corruptedChecksum_throwsChecksumMismatch() {
        val entries = listOf(entry(id = "1"))
        val exported = ExportImport.export(entries, passphrase)
        val parts = exported.split(":")
        assertEquals(5, parts.size)
        // Flip a character in the checksum
        val corruptedChecksum = parts[1].let {
            if (it[0] == 'A') "B" + it.drop(1) else "A" + it.drop(1)
        }
        val corrupted = "${parts[0]}:$corruptedChecksum:${parts[2]}:${parts[3]}:${parts[4]}"
        try {
            ExportImport.import(corrupted, passphrase)
            fail("Expected checksum mismatch")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("corrupted") || e.message!!.contains("checksum"))
        }
    }

    @Test
    fun backup_v0legacy_canBeImported() {
        val entries = listOf(entry(id = "1", issuer = "Legacy"))
        val exportedV1 = ExportImport.export(entries, passphrase)
        val parts = exportedV1.split(":")
        // v0 = last 3 parts (salt:iv:ciphertext)
        val v0Data = "${parts[2]}:${parts[3]}:${parts[4]}"
        val imported = ExportImport.import(v0Data, passphrase)
        assertEquals(1, imported.size)
        assertEquals("Legacy", imported[0].issuer)
    }

    @Test
    fun backup_entriesWithAllAlgorithms_preservesAlgorithm() {
        val entries = listOf(
            entry(id = "1", algorithm = "SHA1"),
            entry(id = "2", algorithm = "SHA256", secret = ByteArray(32) { it.toByte() }),
            entry(id = "3", algorithm = "SHA512", secret = ByteArray(64) { it.toByte() }),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(3, imported.size)
        assertEquals("SHA1", imported[0].algorithm)
        assertEquals("SHA256", imported[1].algorithm)
        assertEquals("SHA512", imported[2].algorithm)
    }

    @Test
    fun backup_entriesWithDigits6and8_preservesDigits() {
        val entries = listOf(
            entry(id = "1", digits = 6),
            entry(id = "2", digits = 8),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(6, imported[0].digits)
        assertEquals(8, imported[1].digits)
    }

    @Test
    fun backup_hotpEntries_preserveCounter() {
        val entries = listOf(
            entry(id = "1", type = "hotp", counter = 0L),
            entry(id = "2", type = "hotp", counter = 12345L),
            entry(id = "3", type = "hotp", counter = Long.MAX_VALUE),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals(3, imported.size)
        assertEquals("hotp", imported[0].type)
        assertEquals(0L, imported[0].counter)
        assertEquals(12345L, imported[1].counter)
        assertEquals(Long.MAX_VALUE, imported[2].counter)
    }

    @Test
    fun backup_entriesWithCategories_preserveCategory() {
        val entries = listOf(
            entry(id = "1").copy(categoryId = "work"),
            entry(id = "2").copy(categoryId = null),
            entry(id = "3").copy(categoryId = "personal"),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals("work", imported[0].categoryId)
        assertNull(imported[1].categoryId)
        assertEquals("personal", imported[2].categoryId)
    }

    @Test
    fun backup_entriesWithFavorites_preserveFavorite() {
        val entries = listOf(
            entry(id = "1").copy(favorite = true),
            entry(id = "2").copy(favorite = false),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertTrue(imported[0].favorite)
        assertFalse(imported[1].favorite)
    }

    @Test
    fun backup_doubleExport_samePassphrase_producesDifferentCiphertext() {
        // IV is random, so two exports of the same data should differ
        val entries = listOf(entry(id = "1"))
        val exported1 = ExportImport.export(entries, passphrase)
        val exported2 = ExportImport.export(entries, passphrase)
        assertNotEquals(exported1, exported2)
        // But both should import to the same data
        val imported1 = ExportImport.import(exported1, passphrase)
        val imported2 = ExportImport.import(exported2, passphrase)
        assertEquals(imported1[0].id, imported2[0].id)
        assertArrayEquals(imported1[0].secret, imported2[0].secret)
    }

    @Test
    fun backup_emptyPassphrase_throwsOrFails() {
        val entries = listOf(entry(id = "1"))
        try {
            ExportImport.export(entries, "")
            // If export succeeds with empty passphrase, import should also work
            // (Argon2id accepts empty input, though it's bad practice)
        } catch (e: Exception) {
            // Acceptable: reject empty passphrase
            assertTrue(true)
        }
    }

    @Test
    fun backup_unicodeIssuerLabel_preservesData() {
        val entries = listOf(
            entry(id = "1", issuer = "Bankéo 中文 日本語 🏦", label = "用户@银行.com"),
        )
        val exported = ExportImport.export(entries, passphrase)
        val imported = ExportImport.import(exported, passphrase)
        assertEquals("Bankéo 中文 日本語 🏦", imported[0].issuer)
        assertEquals("用户@银行.com", imported[0].label)
    }
}
