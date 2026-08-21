package com.cernunnos.authenticator.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [EncryptedStore] using a real device/emulator Context.
 *
 * NOTE: [EncryptedStore.StoredEntry] does not serialize the `iconName` field, so
 * round-trip tests do not assert on `iconName` (it will always come back null).
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStoreTest {
    private lateinit var store: EncryptedStore
    private val passphrase = "testPass123!".toCharArray()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Clear vault prefs before each test
        context.getSharedPreferences("cernunnos_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = EncryptedStore(context)
    }

    private fun entry(id: String = "e1") = TotpEntry(
        id = id, issuer = "Test", label = "user@test.com",
        secret = ByteArray(20) { it.toByte() }
    )

    @Test
    fun initializeVault_thenLoad_returnsEmptyList() {
        store.initializeVault(passphrase)
        assertTrue(store.isInitialized)
        val loaded = store.loadEntries(passphrase)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun saveEntries_thenLoad_returnsSameEntries() {
        store.initializeVault(passphrase)
        val entries = listOf(entry("e1"), entry("e2"), entry("e3"))
        assertTrue(store.saveEntries(entries, passphrase))
        val loaded = store.loadEntries(passphrase)
        assertEquals(3, loaded.size)
        assertEquals("e1", loaded[0].id)
        assertEquals("e2", loaded[1].id)
        assertEquals("e3", loaded[2].id)
    }

    @Test
    fun loadEntries_wrongPassphrase_returnsEmptyList() {
        store.initializeVault(passphrase)
        store.saveEntries(listOf(entry()), passphrase)
        val loaded = store.loadEntries("wrongPass!".toCharArray())
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun saveEntries_preservesAllFields() {
        store.initializeVault(passphrase)
        val entry = TotpEntry(
            id = "full", issuer = "Café", label = "naïve@exämple.com",
            secret = ByteArray(32) { it.toByte() },
            algorithm = "SHA256", digits = 8, period = 60,
            categoryId = "cat1", favorite = true,
            type = "hotp", counter = 42L, iconName = "icon"
        )
        store.saveEntries(listOf(entry), passphrase)
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        val e = loaded[0]
        assertEquals("full", e.id)
        assertEquals("Café", e.issuer)
        assertEquals("naïve@exämple.com", e.label)
        assertArrayEquals(entry.secret, e.secret)
        assertEquals("SHA256", e.algorithm)
        assertEquals(8, e.digits)
        assertEquals(60, e.period)
        assertEquals("cat1", e.categoryId)
        assertTrue(e.favorite)
        assertEquals("hotp", e.type)
        assertEquals(42L, e.counter)
        // iconName IS now serialized by EncryptedStore.StoredEntry
        assertEquals("icon", e.iconName)
    }

    @Test
    fun changePassphrase_oldPassphraseNoLongerWorks() {
        store.initializeVault(passphrase)
        store.saveEntries(listOf(entry()), passphrase)
        val newPass = "newPass456!".toCharArray()
        store.changePassphrase(listOf(entry()), newPass)
        // Old passphrase should fail (salt was rotated)
        val oldLoaded = store.loadEntries(passphrase)
        assertTrue(oldLoaded.isEmpty())
        // New passphrase should work
        val newLoaded = store.loadEntries(newPass)
        assertEquals(1, newLoaded.size)
    }

    @Test
    fun saveEntries_overwrite_replacesData() {
        store.initializeVault(passphrase)
        store.saveEntries(listOf(entry("e1"), entry("e2")), passphrase)
        store.saveEntries(listOf(entry("e3")), passphrase)
        val loaded = store.loadEntries(passphrase)
        assertEquals(1, loaded.size)
        assertEquals("e3", loaded[0].id)
    }

    @Test
    fun loadEntries_notInitialized_returnsEmpty() {
        val loaded = store.loadEntries(passphrase)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun loadEntries_corruptedMainData_fallsBackToBackup() {
        store.initializeVault(passphrase)
        store.saveEntries(listOf(entry("e1")), passphrase)
        // Manually seed the backup slots with the current valid (IV, data) pair,
        // simulating the transient backup state that exists during a save.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("cernunnos_vault", Context.MODE_PRIVATE)
        val validIv = prefs.getString("vault_iv", null)
        val validData = prefs.getString("vault_data", null)
        prefs.edit()
            .putString("vault_iv_backup", validIv)
            .putString("vault_data_backup", validData)
            .commit()
        // Corrupt the main data so decryption of the primary copy fails
        prefs.edit().putString("vault_data", "CORRUPTED").commit()
        // Should recover from the backup copy
        val loaded = store.loadEntries(passphrase)
        assertTrue(loaded.isNotEmpty())
        assertEquals("e1", loaded[0].id)
    }
}
