package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Tests that ExportImport round-trips preserve entry metadata fields
 * (categoryId, favorite, HOTP counter, type, iconName).
 */
class ExportImportMetadataTest {

    private val passphrase = "testpassphrase123"

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
        categoryId: String? = null,
        favorite: Boolean = false,
        iconName: String? = null,
    ) = TotpEntry(
        id = id,
        issuer = issuer,
        label = label,
        secret = secret,
        algorithm = algorithm,
        digits = digits,
        period = period,
        type = type,
        counter = counter,
        categoryId = categoryId,
        favorite = favorite,
        iconName = iconName,
    )

    private fun roundTrip(entries: List<TotpEntry>): List<TotpEntry> {
        val exported = ExportImport.export(entries, passphrase)
        return ExportImport.import(exported, passphrase)
    }

    @Test
    fun exportImport_roundTrip_preservesCategoryId() {
        val entries = listOf(entry(id = "c1", categoryId = "work"))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertEquals("work", imported[0].categoryId)
    }

    @Test
    fun exportImport_roundTrip_preservesFavorite() {
        val entries = listOf(entry(id = "f1", favorite = true))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertTrue(imported[0].favorite)
    }

    @Test
    fun exportImport_roundTrip_preservesNullCategoryId() {
        val entries = listOf(entry(id = "n1", categoryId = null))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertNull(imported[0].categoryId)
    }

    @Test
    fun exportImport_roundTrip_preservesFavoriteFalse() {
        val entries = listOf(entry(id = "f2", favorite = false))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertFalse(imported[0].favorite)
    }

    @Test
    fun exportImport_roundTrip_preservesHotpCounter() {
        val entries = listOf(entry(id = "h1", type = "hotp", counter = 42L))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertEquals(42L, imported[0].counter)
    }

    @Test
    fun exportImport_roundTrip_preservesType() {
        val entries = listOf(entry(id = "h2", type = "hotp", counter = 7L))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertEquals("hotp", imported[0].type)
    }

    @Test
    fun exportImport_roundTrip_preservesIconName() {
        val entries = listOf(entry(id = "i1", iconName = "Shield"))
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        assertEquals("Shield", imported[0].iconName)
    }

    @Test
    fun exportImport_roundTrip_preservesAllFieldsCombined() {
        val secret = ByteArray(32) { (it * 5).toByte() }
        val entries = listOf(
            entry(
                id = "full1",
                issuer = "FullIssuer",
                label = "full@user.com",
                secret = secret,
                algorithm = "SHA256",
                digits = 8,
                period = 60,
                type = "hotp",
                counter = 999L,
                categoryId = "personal",
                favorite = true,
                iconName = "BankIcon",
            )
        )
        val imported = roundTrip(entries)
        assertEquals(1, imported.size)
        val e = imported[0]
        assertEquals("full1", e.id)
        assertEquals("FullIssuer", e.issuer)
        assertEquals("full@user.com", e.label)
        assertArrayEquals(secret, e.secret)
        assertEquals("SHA256", e.algorithm)
        assertEquals(8, e.digits)
        assertEquals(60, e.period)
        assertEquals("hotp", e.type)
        assertEquals(999L, e.counter)
        assertEquals("personal", e.categoryId)
        assertTrue(e.favorite)
        assertEquals("BankIcon", e.iconName)
    }
}
