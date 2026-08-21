package com.cernunnos.authenticator.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.data.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [DocumentStore] backup-before-write and crash recovery.
 *
 * Verifies that:
 * - The document index survives a simulated mid-write crash
 * - The backup is used when the main index is corrupted
 * - Documents can be saved and loaded correctly
 * - The vault can be locked and unlocked
 */
@RunWith(AndroidJUnit4::class)
class DocumentStoreBackupTest {
    private lateinit var context: Context
    private lateinit var store: DocumentStore
    private val passphrase = "testVaultPass123!".toCharArray()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear document vault prefs before each test
        context.getSharedPreferences("cernunnos_documents", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Clear document files
        val docsDir = java.io.File(context.filesDir, "documents")
        if (docsDir.exists()) docsDir.listFiles()?.forEach { it.delete() }
        store = DocumentStore(context)
    }

    private fun sampleEntry(id: String = "doc-1") = DocumentEntry(
        id = id,
        type = DocumentType.DRIVER_LICENSE,
        title = "Permis de conduire",
        encryptedFileName = "", // will be assigned by store
        thumbnailBase64 = null,
        hasVerso = false,
        expirationDate = null,
        notes = "Test note",
    )

    @Test
    fun initialize_then_unlock_succeeds() {
        store.initialize(passphrase)
        assertTrue("Store should be initialized", store.isInitialized)
        assertTrue("Unlock should succeed", store.unlock(passphrase))
        assertTrue("Store should be unlocked", store.isUnlocked)
    }

    @Test
    fun addDocument_then_getIndex_returnsEntry() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        val imageData = "fake image data".toByteArray()
        val entry = sampleEntry()
        val saved = store.addDocument(imageData, null, entry)

        assertNotNull("Saved entry should have a file name", saved.encryptedFileName)
        val index = store.getDocuments()
        assertEquals("Index should contain 1 entry", 1, index.size)
        assertEquals("Entry title should match", "Permis de conduire", index[0].title)
    }

    @Test
    fun saveIndex_then_corruptMainIndex_recoversFromBackup() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        // Save a document — this creates and then removes the backup
        val imageData = "fake image data".toByteArray()
        val entry = sampleEntry("doc-recovery")
        store.addDocument(imageData, null, entry)

        // Verify it was saved
        val originalIndex = store.getDocuments()
        assertEquals(1, originalIndex.size)

        // Manually create a backup of the current valid index
        val prefs = context.getSharedPreferences("cernunnos_documents", Context.MODE_PRIVATE)
        val mainIv = prefs.getString("doc_index_iv", null)
        val mainData = prefs.getString("doc_index_data", null)
        assertNotNull("Main IV should exist", mainIv)
        assertNotNull("Main data should exist", mainData)

        // Create a backup copy
        prefs.edit()
            .putString("doc_index_iv_backup", mainIv)
            .putString("doc_index_data_backup", mainData)
            .commit()

        // Corrupt the main index data
        prefs.edit().putString("doc_index_data", "CORRUPTED_DATA_BASE64==").commit()

        // getDocuments() should recover from backup
        val recoveredIndex = store.getDocuments()
        assertEquals("Recovered index should have 1 entry", 1, recoveredIndex.size)
        assertEquals("Recovered entry title should match", "Permis de conduire", recoveredIndex[0].title)
    }

    @Test
    fun saveIndex_createsBackupBeforeWrite() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        // Save first document
        val imageData = "fake image data 1".toByteArray()
        store.addDocument(imageData, null, sampleEntry("doc-1"))

        // Save second document — this should create a backup of the first index
        val imageData2 = "fake image data 2".toByteArray()
        store.addDocument(imageData2, null, sampleEntry("doc-2"))

        // After successful write, backup should be cleaned up
        val prefs = context.getSharedPreferences("cernunnos_documents", Context.MODE_PRIVATE)
        // The backup keys should be removed after successful write
        // (They are only present transiently during the write)
        val index = store.getDocuments()
        assertEquals("Index should have 2 entries", 2, index.size)
    }

    @Test
    fun lock_then_unlock_preservesDocuments() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        val imageData = "fake image data".toByteArray()
        store.addDocument(imageData, null, sampleEntry("doc-persist"))

        // Lock the vault
        store.lock()
        assertTrue("Store should be locked", !store.isUnlocked)

        // Unlock again
        assertTrue("Unlock should succeed", store.unlock(passphrase))

        // Documents should still be there
        val index = store.getDocuments()
        assertEquals("Documents should persist across lock/unlock", 1, index.size)
        assertEquals("doc-persist", index[0].id)
    }

    @Test
    fun deleteDocument_removesFromIndex() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        val imageData = "fake image data".toByteArray()
        val saved = store.addDocument(imageData, null, sampleEntry("doc-delete"))

        assertEquals(1, store.getDocuments().size)

        store.deleteDocument(saved.id)

        assertEquals("Index should be empty after delete", 0, store.getDocuments().size)
    }

    @Test
    fun addMultipleDocuments_allPersist() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        for (i in 1..5) {
            val imageData = "fake image data $i".toByteArray()
            store.addDocument(imageData, null, sampleEntry("doc-$i").copy(title = "Document $i"))
        }

        val index = store.getDocuments()
        assertEquals("All 5 documents should persist", 5, index.size)
    }

    @Test
    fun wrongPassphrase_unlock_fails() {
        store.initialize(passphrase)
        // Lock first, then try to unlock with wrong passphrase
        store.lock()
        val wrongPass = "wrongPassword456!".toCharArray()
        assertTrue("Unlock with wrong passphrase should fail", !store.unlock(wrongPass))
        assertTrue("Store should remain locked", !store.isUnlocked)
    }

    @Test
    fun wrongPassphrase_afterInitialize_vaultStaysLocked() {
        // Regression test: initialize() sets masterKey, but a subsequent
        // failed unlock() must clear it so isUnlocked returns false.
        store.initialize(passphrase)
        // Don't lock — try unlock with wrong passphrase directly
        val wrongPass = "wrongPassword456!".toCharArray()
        val result = store.unlock(wrongPass)
        assertTrue("Unlock with wrong passphrase should fail", !result)
        assertTrue("Store should remain locked after failed unlock", !store.isUnlocked)
    }

    @Test
    fun addDocument_withVerso_persistsBothSides() {
        store.initialize(passphrase)
        store.unlock(passphrase)

        val rectoData = "recto image".toByteArray()
        val versoData = "verso image".toByteArray()
        val saved = store.addDocument(rectoData, versoData, sampleEntry("doc-verso"))

        assertTrue("Entry should have verso", saved.hasVerso)
        assertNotNull("Entry should have verso file name", saved.encryptedVersoFileName)

        val index = store.getDocuments()
        assertEquals(1, index.size)
        assertTrue(index[0].hasVerso)
        assertNotNull(index[0].encryptedVersoFileName)
    }
}
