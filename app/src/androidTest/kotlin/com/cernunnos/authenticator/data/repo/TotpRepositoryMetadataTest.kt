package com.cernunnos.authenticator.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [TotpRepository] focused on metadata preservation
 * across lock/unlock cycles and passphrase changes.
 *
 * Each test exercises a mutation (add/update/changePassphrase) then performs a
 * lock → unlock cycle and asserts that metadata (iconName, categoryId, favorite,
 * type, counter) survives the round-trip through encrypted storage.
 */
@RunWith(AndroidJUnit4::class)
class TotpRepositoryMetadataTest {
    private lateinit var repo: TotpRepository
    private val passphrase = "testpassphrase123".toCharArray()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cernunnos_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repo = TotpRepository(context)
        repo.initializeWithPassphrase(passphrase)
    }

    private fun baseEntry(id: String = "e1") = TotpEntry(
        id = id, issuer = "Test", label = "user@test.com",
        secret = ByteArray(20) { it.toByte() }
    )

    @Test
    fun addEntry_withIconName_loadsIconNameAfterLockUnlock() {
        repo.addEntry(baseEntry().copy(iconName = "Shield"))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertEquals("Shield", got.iconName)
    }

    @Test
    fun addEntry_withCategoryId_loadsCategoryIdAfterLockUnlock() {
        repo.addEntry(baseEntry().copy(categoryId = "work"))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertEquals("work", got.categoryId)
    }

    @Test
    fun addEntry_withFavorite_loadsFavoriteAfterLockUnlock() {
        repo.addEntry(baseEntry().copy(favorite = true))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertTrue(got.favorite)
    }

    @Test
    fun addHotpEntry_counterPreservedAfterLockUnlock() {
        repo.addEntry(baseEntry("h1").copy(type = "hotp", counter = 42L))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("h1")!!
        assertEquals("hotp", got.type)
        assertEquals(42L, got.counter)
    }

    @Test
    fun updateEntry_preservesIconName() {
        repo.addEntry(baseEntry())
        repo.updateEntry(baseEntry().copy(iconName = "Shield"))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertEquals("Shield", got.iconName)
    }

    @Test
    fun updateEntry_preservesCategoryId() {
        repo.addEntry(baseEntry())
        repo.updateEntry(baseEntry().copy(categoryId = "work"))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertEquals("work", got.categoryId)
    }

    @Test
    fun updateEntry_preservesTypeAndCounter() {
        repo.addEntry(baseEntry()) // TOTP by default
        repo.updateEntry(baseEntry().copy(type = "hotp", counter = 5L))
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(passphrase))
        val got = repo.getEntry("e1")!!
        assertEquals("hotp", got.type)
        assertEquals(5L, got.counter)
    }

    @Test
    fun changePassphrase_preservesAllMetadata() {
        repo.addEntry(baseEntry().copy(
            iconName = "Shield",
            categoryId = "work",
            favorite = true,
        ))
        val newPass = "newPassphrase456!".toCharArray()
        repo.changePassphrase(newPass)
        repo.lock()
        assertTrue(repo.unlockWithPassphrase(newPass))
        val got = repo.getEntry("e1")
        assertNotNull(got)
        assertEquals("Shield", got!!.iconName)
        assertEquals("work", got.categoryId)
        assertTrue(got.favorite)
    }
}
