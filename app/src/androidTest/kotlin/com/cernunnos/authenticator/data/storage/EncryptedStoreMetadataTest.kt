package com.cernunnos.authenticator.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [EncryptedStore] focused on metadata round-trip
 * preservation (iconName, categoryId, favorite, type, counter).
 *
 * Each test performs a save → load cycle and asserts that NO metadata is lost.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStoreMetadataTest {
    private lateinit var store: EncryptedStore
    private val passphrase = "testpassphrase123".toCharArray()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cernunnos_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = EncryptedStore(context)
        store.initializeVault(passphrase)
    }

    private fun baseEntry(id: String = "e1") = TotpEntry(
        id = id, issuer = "Test", label = "user@test.com",
        secret = ByteArray(20) { it.toByte() }
    )

    @Test
    fun saveLoad_preservesIconName() {
        val entry = baseEntry().copy(iconName = "Shield")
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertEquals("Shield", loaded[0].iconName)
    }

    @Test
    fun saveLoad_preservesNullIconName() {
        val entry = baseEntry().copy(iconName = null)
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertNull(loaded[0].iconName)
    }

    @Test
    fun saveLoad_preservesCategoryId() {
        val entry = baseEntry().copy(categoryId = "work")
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertEquals("work", loaded[0].categoryId)
    }

    @Test
    fun saveLoad_preservesNullCategoryId() {
        val entry = baseEntry().copy(categoryId = null)
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertNull(loaded[0].categoryId)
    }

    @Test
    fun saveLoad_preservesFavorite() {
        val entry = baseEntry().copy(favorite = true)
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertTrue(loaded[0].favorite)
    }

    @Test
    fun saveLoad_preservesHotpType() {
        val entry = baseEntry().copy(type = "hotp")
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertEquals("hotp", loaded[0].type)
    }

    @Test
    fun saveLoad_preservesHotpCounter() {
        val entry = baseEntry().copy(type = "hotp", counter = 42L)
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertEquals(42L, loaded[0].counter)
    }

    @Test
    fun saveLoad_preservesAllFieldsCombined() {
        val entry = baseEntry(id = "full").copy(
            categoryId = "work",
            favorite = true,
            type = "hotp",
            counter = 42L,
            iconName = "Shield",
        )
        assertTrue(store.saveEntries(listOf(entry), passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        val e = loaded[0]
        assertEquals("full", e.id)
        assertEquals("work", e.categoryId)
        assertTrue(e.favorite)
        assertEquals("hotp", e.type)
        assertEquals(42L, e.counter)
        assertEquals("Shield", e.iconName)
    }

    @Test
    fun saveLoad_multipleEntries_preservesAllMetadata() {
        val entries = listOf(
            baseEntry("e1").copy(iconName = "Shield", categoryId = "work", favorite = true),
            baseEntry("e2").copy(iconName = null, categoryId = null, favorite = false,
                type = "hotp", counter = 7L),
            baseEntry("e3").copy(iconName = "Lock", categoryId = "personal", favorite = true,
                type = "hotp", counter = 100L),
        )
        assertTrue(store.saveEntries(entries, passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(3, loaded.size)

        val e1 = loaded.find { it.id == "e1" }!!
        assertEquals("Shield", e1.iconName)
        assertEquals("work", e1.categoryId)
        assertTrue(e1.favorite)

        val e2 = loaded.find { it.id == "e2" }!!
        assertNull(e2.iconName)
        assertNull(e2.categoryId)
        assertTrue(!e2.favorite)
        assertEquals("hotp", e2.type)
        assertEquals(7L, e2.counter)

        val e3 = loaded.find { it.id == "e3" }!!
        assertEquals("Lock", e3.iconName)
        assertEquals("personal", e3.categoryId)
        assertTrue(e3.favorite)
        assertEquals("hotp", e3.type)
        assertEquals(100L, e3.counter)
    }
}
