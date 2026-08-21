package com.cernunnos.authenticator.data.model

import com.cernunnos.authenticator.constants.TotpConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TotpEntry] equals/hashCode/copy/default values.
 */
class TotpEntryTest {

    private fun baseEntry(
        id: String = "id-1",
        issuer: String = "Issuer",
        label: String = "user@example.com",
        secret: ByteArray = ByteArray(20) { it.toByte() },
    ): TotpEntry = TotpEntry(
        id = id,
        issuer = issuer,
        label = label,
        secret = secret,
    )

    // --- equals ---

    @Test
    fun equals_sameIdDifferentSecret_returnsTrue() {
        val a = baseEntry(secret = ByteArray(20) { 0x01 })
        val b = baseEntry(secret = ByteArray(20) { 0x02 })
        // secret is intentionally excluded from equals
        assertEquals(a, b)
    }

    @Test
    fun equals_differentId_returnsFalse() {
        val a = baseEntry(id = "id-1")
        val b = baseEntry(id = "id-2")
        assertNotEquals(a, b)
    }

    @Test
    fun equals_sameObject_returnsTrue() {
        val a = baseEntry()
        assertTrue(a == a)
    }

    @Test
    fun equals_null_returnsFalse() {
        val a = baseEntry()
        assertFalse(a.equals(null))
    }

    @Test
    fun equals_differentType_returnsFalse() {
        val a = baseEntry()
        assertFalse(a.equals("not a TotpEntry"))
    }

    // --- hashCode ---

    @Test
    fun hashCode_sameId_sameHashCode() {
        val a = baseEntry(id = "id-1")
        val b = baseEntry(id = "id-1")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun hashCode_differentId_differentHashCode() {
        val a = baseEntry(id = "id-1")
        val b = baseEntry(id = "id-2")
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    // --- default values ---

    @Test
    fun defaultValues_areCorrect() {
        val entry = TotpEntry(
            id = "id-1",
            issuer = "Issuer",
            label = "user@example.com",
            secret = ByteArray(20),
        )
        assertEquals(TotpConfig.TYPE_TOTP, entry.type)
        assertEquals(TotpConfig.ALGO_SHA1, entry.algorithm)
        assertEquals(TotpConfig.DEFAULT_DIGITS, entry.digits)
        assertEquals(TotpConfig.DEFAULT_PERIOD, entry.period)
        assertEquals(0L, entry.counter)
        assertFalse(entry.favorite)
        assertNull(entry.categoryId)
        assertNull(entry.iconName)
    }

    // --- copy ---

    @Test
    fun copy_preservesAllFields() {
        val entry = TotpEntry(
            id = "id-1",
            issuer = "Issuer",
            label = "user@example.com",
            secret = ByteArray(20) { it.toByte() },
            algorithm = "SHA256",
            digits = 8,
            period = 60,
            categoryId = "cat-1",
            favorite = true,
            type = "hotp",
            counter = 42L,
            iconName = "icon",
        )
        val copy = entry.copy()
        assertEquals(entry, copy)
        assertEquals(entry.id, copy.id)
        assertEquals(entry.issuer, copy.issuer)
        assertEquals(entry.label, copy.label)
        assertEquals(entry.algorithm, copy.algorithm)
        assertEquals(entry.digits, copy.digits)
        assertEquals(entry.period, copy.period)
        assertEquals(entry.categoryId, copy.categoryId)
        assertEquals(entry.favorite, copy.favorite)
        assertEquals(entry.type, copy.type)
        assertEquals(entry.counter, copy.counter)
        assertEquals(entry.iconName, copy.iconName)
    }

    @Test
    fun copy_withModifications_works() {
        val entry = baseEntry()
        val modified = entry.copy(id = "new-id", favorite = true)
        assertEquals("new-id", modified.id)
        assertTrue(modified.favorite)
        // unchanged fields preserved
        assertEquals(entry.issuer, modified.issuer)
        assertEquals(entry.label, modified.label)
    }

    @Test
    fun entry_withCustomParams_preservesThem() {
        val entry = TotpEntry(
            id = "id-1",
            issuer = "Issuer",
            label = "user@example.com",
            secret = ByteArray(20),
            algorithm = "SHA512",
            digits = 8,
            period = 15,
            categoryId = "cat-9",
            favorite = true,
            type = "hotp",
            counter = 99L,
            iconName = "custom_icon",
        )
        assertEquals("SHA512", entry.algorithm)
        assertEquals(8, entry.digits)
        assertEquals(15, entry.period)
        assertEquals("cat-9", entry.categoryId)
        assertTrue(entry.favorite)
        assertEquals("hotp", entry.type)
        assertEquals(99L, entry.counter)
        assertEquals("custom_icon", entry.iconName)
    }
}
