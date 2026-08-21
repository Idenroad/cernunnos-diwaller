package com.cernunnos.authenticator.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [TotpRepository] using a real device/emulator Context.
 *
 * Covers: init/unlock/lock lifecycle, CRUD, passphrase change, concurrent writes,
 * HOTP counter preservation, and full-field round-trip persistence.
 *
 * NOTE: [TotpRepository] delegates persistence to [com.cernunnos.authenticator.data.storage.EncryptedStore],
 * which does not serialize the `iconName` field. Round-trip tests therefore do not
 * assert on `iconName` (it comes back null).
 */
@RunWith(AndroidJUnit4::class)
class TotpRepositoryTest {
    private lateinit var repo: TotpRepository
    private val passphrase = "testPass123!".toCharArray()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cernunnos_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repo = TotpRepository(context)
    }

    private fun entry(id: String = "e1") = TotpEntry(
        id = id, issuer = "Test", label = "user@test.com",
        secret = ByteArray(20) { it.toByte() }
    )

    @Test
    fun initializeWithPassphrase_thenIsInitialized() {
        repo.initializeWithPassphrase(passphrase)
        assertTrue(repo.isInitialized)
        assertTrue(repo.isUnlocked)
    }

    @Test
    fun addEntry_thenGetEntry_returnsEntry() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        val got = repo.getEntry("e1")
        assertNotNull(got)
        assertEquals("e1", got!!.id)
    }

    @Test
    fun addEntry_thenRemoveEntry_entryGone() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        repo.removeEntry("e1")
        assertNull(repo.getEntry("e1"))
        assertTrue(repo.entries.isEmpty())
    }

    @Test
    fun addEntry_thenUpdateEntry_entryUpdated() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        val updated = entry("e1").copy(issuer = "Updated")
        repo.updateEntry(updated)
        assertEquals("Updated", repo.getEntry("e1")!!.issuer)
    }

    @Test
    fun lock_thenIsUnlockedFalse() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        repo.lock()
        assertFalse(repo.isUnlocked)
        assertTrue(repo.entries.isEmpty())
    }

    @Test
    fun unlockWithPassphrase_loadsEntries() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        repo.addEntry(entry("e2"))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        assertEquals(2, repo.entries.size)
    }

    @Test
    fun addMultipleEntries_allPresent() {
        repo.initializeWithPassphrase(passphrase)
        for (i in 1..10) {
            repo.addEntry(entry("e$i"))
        }
        assertEquals(10, repo.entries.size)
    }

    @Test
    fun changePassphrase_newPassphraseWorks() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        val newPass = "newPass456!".toCharArray()
        repo.changePassphrase(newPass)
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(newPass))
        assertEquals(1, repo.entries.size)
    }

    @Test
    fun changePassphrase_oldPassphraseFails() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        val newPass = "newPass456!".toCharArray()
        repo.changePassphrase(newPass)
        repo.lock()
        repo.unlockWithPassphrase(passphrase)
        // Old passphrase should load empty (decrypt fails — salt was rotated)
        assertTrue(repo.entries.isEmpty())
    }

    @Test
    fun getAllEntriesForExport_returnsAllEntries() {
        repo.initializeWithPassphrase(passphrase)
        repo.addEntry(entry("e1"))
        repo.addEntry(entry("e2"))
        repo.addEntry(entry("e3"))
        val exported = repo.getAllEntriesForExport()
        assertEquals(3, exported.size)
    }

    @Test
    fun concurrentAddEntries_noCorruption() = runBlocking {
        repo.initializeWithPassphrase(passphrase)
        coroutineScope {
            val jobs = (1..20).map { i ->
                async(Dispatchers.IO) {
                    repo.addEntry(entry("e$i"))
                }
            }
            jobs.awaitAll()
        }
        // All 20 entries should be present (no corruption)
        assertEquals(20, repo.entries.size)
        // Each entry should be unique
        val ids = repo.entries.map { it.id }.toSet()
        assertEquals(20, ids.size)
    }

    @Test
    fun hotpEntry_counterPreserved() {
        repo.initializeWithPassphrase(passphrase)
        val hotpEntry = TotpEntry(
            id = "h1", issuer = "HOTP", label = "hotp@test.com",
            secret = ByteArray(20), type = "hotp", counter = 42L
        )
        repo.addEntry(hotpEntry)
        val got = repo.getEntry("h1")!!
        assertEquals("hotp", got.type)
        assertEquals(42L, got.counter)
    }

    @Test
    fun entryWithAllFields_preservedAfterRoundTrip() {
        repo.initializeWithPassphrase(passphrase)
        val entry = TotpEntry(
            id = "full", issuer = "Café", label = "naïve@exämple.com",
            secret = ByteArray(32) { it.toByte() },
            algorithm = "SHA512", digits = 8, period = 15,
            categoryId = "cat1", favorite = true,
            type = "hotp", counter = 99L, iconName = "Shield"
        )
        repo.addEntry(entry)
        repo.lock()
        repo.unlockWithPassphrase(passphrase)
        val got = repo.getEntry("full")!!
        assertEquals("Café", got.issuer)
        assertEquals("naïve@exämple.com", got.label)
        assertArrayEquals(entry.secret, got.secret)
        assertEquals("SHA512", got.algorithm)
        assertEquals(8, got.digits)
        assertEquals(15, got.period)
        assertEquals("cat1", got.categoryId)
        assertTrue(got.favorite)
        assertEquals("hotp", got.type)
        assertEquals(99L, got.counter)
        // iconName IS now serialized by EncryptedStore.StoredEntry
        assertEquals("Shield", got.iconName)
    }
}
