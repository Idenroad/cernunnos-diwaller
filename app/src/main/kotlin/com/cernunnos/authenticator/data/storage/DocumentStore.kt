package com.cernunnos.authenticator.data.storage

import android.content.Context
import android.util.Base64
import android.util.Log
import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.util.IOUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Encrypted storage for the Documents vault.
 *
 * This is a SEPARATE vault from the TOTP vault:
 * - Different salt (KEY_DOC_SALT)
 * - Different passphrase (set independently by the user)
 * - Different storage directory (filesDir/documents/)
 *
 * Each document photo is stored as an individual encrypted file.
 * The document index (metadata) is stored as encrypted JSON in SharedPreferences.
 *
 * Security:
 * - AES-256-GCM for both index and individual files
 * - Argon2id for key derivation from the user's documents passphrase
 * - The derived key is kept in memory only while unlocked, then zeroed on lock()
 */
class DocumentStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = ReentrantLock()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // Documents directory — each document is an encrypted file here
    private val docsDir = File(context.filesDir, "documents").also { it.mkdirs() }

    // In-memory key — only set while unlocked
    private var masterKey: ByteArray? = null

    // In-memory cache of the decrypted document index — avoids re-decrypting on every CRUD op
    private var indexCache: List<DocumentEntry>? = null

    val isInitialized: Boolean get() = prefs.getBoolean(KEY_SETUP, false)
    val isUnlocked: Boolean get() = masterKey != null

    // ── Initialization & unlock ──

    /**
     * Initialize the documents vault with a passphrase.
     * Creates a new salt and an empty index.
     */
    fun initialize(passphrase: CharArray) {
        lock.withLock {
            val salt = Argon2id.generateSalt()
            val success = prefs.edit()
                .putString(KEY_SALT, encodeB64(salt))
                .putBoolean(KEY_SETUP, true)
                .commit()
            if (!success) error("Failed to initialize documents vault")
            masterKey = Argon2id.deriveKey(passphrase, salt)
            saveIndex(emptyList())
        }
    }

    /**
     * Unlock the vault with a passphrase.
     * Returns true if the passphrase is correct (verified by decrypting the index).
     */
    fun unlock(passphrase: CharArray): Boolean {
        lock.withLock {
            // Clear any existing master key before attempting unlock
            // so that a failed unlock leaves the vault locked.
            masterKey?.fill(0)
            masterKey = null

            val salt = getSalt() ?: return false
            val key = Argon2id.deriveKey(passphrase, salt)
            // Verify by trying to decrypt the index.
            // The index always exists after initialize() (which saves an empty list),
            // so if the IV is missing the vault is in an unexpected state — reject.
            return try {
                val ivB64 = prefs.getString(KEY_INDEX_IV, null)
                val dataB64 = prefs.getString(KEY_INDEX_DATA, null)
                if (ivB64 != null && dataB64 != null) {
                    val iv = decodeB64(ivB64)
                    val ciphertext = decodeB64(dataB64)
                    CryptoManager.decryptWithKey(
                        CryptoManager.EncryptedData(salt, iv, ciphertext),
                        key,
                    )
                    masterKey = key
                    true
                } else {
                    // No index — vault is in an unexpected state, reject unlock
                    key.fill(0)
                    false
                }
            } catch (e: Exception) {
                key.fill(0)
                false
            }
        }
    }

    /** Lock the vault — zero the master key. */
    fun lock() {
        lock.withLock {
            masterKey?.fill(0)
            masterKey = null
            indexCache = null
        }
    }

    // ── CRUD ──

    /**
     * Save an encrypted document file and add its metadata to the index.
     * @param imageData Raw (compressed) recto image bytes.
     * @param versoImageData Raw (compressed) verso image bytes, or null for recto only.
     * @param entry Document metadata (without encryptedFileName — it will be assigned).
     * @return The updated entry with the encrypted file name(s).
     */
    fun addDocument(imageData: ByteArray, versoImageData: ByteArray?, entry: DocumentEntry): DocumentEntry {
        lock.withLock {
            val mk = masterKey ?: run {
                error("Documents vault is locked")
            }
            val fileName = "doc_${entry.id}.enc"
            encryptAndWrite(imageData, fileName, mk)
            var versoFileName: String? = null
            if (versoImageData != null) {
                versoFileName = "doc_${entry.id}_verso.enc"
                encryptAndWrite(versoImageData, versoFileName, mk)
            }
            val updated = entry.copy(encryptedFileName = fileName, encryptedVersoFileName = versoFileName, hasVerso = versoImageData != null)
            val index = getIndex()
            saveIndex(index + updated)
            return updated
        }
    }

    private fun encryptAndWrite(data: ByteArray, fileName: String, key: ByteArray) {
        val encrypted = CryptoManager.encryptWithKey(data, key)
        val file = File(docsDir, fileName)
        file.outputStream().use { out ->
            out.write(encrypted.iv)
            out.write(encrypted.ciphertext)
        }
    }

    /**
     * Update a document's metadata (without changing the image).
     */
    fun updateDocument(entry: DocumentEntry) {
        lock.withLock {
            val index = getIndex()
            val updated = index.map { if (it.id == entry.id) entry.copy(updatedAt = System.currentTimeMillis()) else it }
            saveIndex(updated)
        }
    }

    /**
     * Replace a document's image.
     */
    fun replaceImage(entry: DocumentEntry, imageData: ByteArray) {
        lock.withLock {
            val mk = masterKey ?: error("Documents vault is locked")
            // Atomic write pattern: write to a temp file first, then delete the
            // old file, then rename the temp file to the final name. This ensures
            // that if a crash occurs between delete and write, the old file is
            // still intact (or the tmp file can be cleaned up).
            val encrypted = CryptoManager.encryptWithKey(imageData, mk)
            val tmpFile = File(docsDir, entry.encryptedFileName + ".tmp")
            tmpFile.outputStream().use { out ->
                out.write(encrypted.iv)
                out.write(encrypted.ciphertext)
            }
            // Delete old file only after the new one is safely written
            File(docsDir, entry.encryptedFileName).delete()
            // Rename temp file to the final name
            if (!tmpFile.renameTo(File(docsDir, entry.encryptedFileName))) {
                // Fallback: if rename fails (e.g. cross-device), copy then delete
                tmpFile.copyTo(File(docsDir, entry.encryptedFileName), overwrite = true)
                tmpFile.delete()
            }
            // Update the index to reflect the new updatedAt timestamp
            val index = getIndex()
            val updated = index.map { if (it.id == entry.id) it.copy(updatedAt = System.currentTimeMillis()) else it }
            saveIndex(updated)
        }
    }

    /**
     * Decrypt and return a document's recto image bytes.
     */
    fun getDocumentImage(entry: DocumentEntry): ByteArray {
        lock.withLock {
            val mk = masterKey ?: error("Documents vault is locked")
            return decryptFile(entry.encryptedFileName, mk)
        }
    }

    /**
     * Decrypt and return a document's verso image bytes, or null if no verso.
     */
    fun getDocumentVersoImage(entry: DocumentEntry): ByteArray? {
        lock.withLock {
            val mk = masterKey ?: error("Documents vault is locked")
            val versoName = entry.encryptedVersoFileName ?: return null
            return try { decryptFile(versoName, mk) } catch (e: Exception) { null }
        }
    }

    private fun decryptFile(fileName: String, key: ByteArray): ByteArray {
        val file = File(docsDir, fileName)
        if (!file.exists()) error("Document file not found: $fileName")
        // Bounded read to prevent OOM on corrupted/oversized files.
        if (file.length() > MAX_DOC_FILE_BYTES) {
            error("Encrypted file too large: ${file.length()} bytes")
        }
        val bytes = file.inputStream().use { IOUtils.readBounded(it, MAX_DOC_FILE_BYTES) }
        val ivSize = com.cernunnos.authenticator.constants.SecurityConfig.IV_SIZE
        if (bytes.size < ivSize) error("Encrypted file is corrupted (too short)")
        val iv = bytes.copyOfRange(0, ivSize)
        val ciphertext = bytes.copyOfRange(ivSize, bytes.size)
        return CryptoManager.decryptWithKey(
            CryptoManager.EncryptedData(ByteArray(0), iv, ciphertext),
            key,
        )
    }

    /**
     * Delete a document and its encrypted file(s).
     */
    fun deleteDocument(id: String) {
        lock.withLock {
            val index = getIndex()
            val entry = index.find { it.id == id } ?: return
            File(docsDir, entry.encryptedFileName).delete()
            entry.encryptedVersoFileName?.let { File(docsDir, it).delete() }
            saveIndex(index.filterNot { it.id == id })
        }
    }

    /**
     * Get all document metadata (index).
     */
    fun getDocuments(): List<DocumentEntry> {
        lock.withLock {
            // Return cached index if available (avoids re-decrypting on every call)
            indexCache?.let { return it }
            val index = getIndex()
            indexCache = index
            return index
        }
    }

    /**
     * Export a single document as an encrypted file (for sharing with a trusted person).
     * The recipient needs the same passphrase to decrypt.
     *
     * Format v1: "CERNDV1:" + base64(salt) + ":" + base64(iv) + ":" + base64(ciphertext) + ":" + base64(sha256(payload))
     * Legacy format (no header): [salt (16)][iv (12)][ciphertext] — still importable for backward compat.
     */
    fun exportDocument(id: String, passphrase: String): ByteArray? {
        lock.withLock {
            val entry = getIndex().find { it.id == id } ?: return null
            val imageBytes = getDocumentImage(entry)
            // Re-encrypt with a fresh passphrase-derived key
            val salt = Argon2id.generateSalt()
            val pass = passphrase.toCharArray()
            val encrypted = try {
                CryptoManager.encrypt(imageBytes, pass, salt)
            } finally {
                pass.fill(0.toChar())
            }
            // v1 format with version header and checksum
            val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
            val ivB64 = android.util.Base64.encodeToString(encrypted.iv, android.util.Base64.NO_WRAP)
            val ctB64 = android.util.Base64.encodeToString(encrypted.ciphertext, android.util.Base64.NO_WRAP)
            val payload = "$saltB64:$ivB64:$ctB64"
            val checksum = java.security.MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray())
            val checksumB64 = android.util.Base64.encodeToString(checksum, android.util.Base64.NO_WRAP)
            return "CERNDV1:$payload:$checksumB64".toByteArray()
        }
    }

    /**
     * Import a shared encrypted document.
     * Supports both v1 format ("CERNDV1:...") and legacy binary format ([salt][iv][ciphertext]).
     */
    fun importDocument(data: ByteArray, passphrase: String, title: String, type: com.cernunnos.authenticator.data.model.DocumentType): DocumentEntry? {
        lock.withLock {
            val mk = masterKey ?: error("Documents vault is locked")
            val pass = passphrase.toCharArray()
            val imageBytes = try {
                // Try v1 format first
                val text = String(data, Charsets.UTF_8).trim()
                if (text.startsWith("CERNDV1:")) {
                    val parts = text.split(":")
                    if (parts.size != 5) return null
                    val payload = "${parts[1]}:${parts[2]}:${parts[3]}"
                    val expectedChecksum = parts[4]
                    val actualChecksum = android.util.Base64.encodeToString(
                        java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()),
                        android.util.Base64.NO_WRAP,
                    )
                    if (actualChecksum != expectedChecksum) {
                        android.util.Log.w("DocumentStore", "Import checksum mismatch — file may be corrupted")
                        return null
                    }
                    val salt = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
                    val iv = android.util.Base64.decode(parts[2], android.util.Base64.NO_WRAP)
                    val ciphertext = android.util.Base64.decode(parts[3], android.util.Base64.NO_WRAP)
                    CryptoManager.decrypt(
                        CryptoManager.EncryptedData(salt, iv, ciphertext),
                        pass,
                    )
                } else {
                    // Legacy binary format: [salt (16)][iv (12)][ciphertext]
                    if (data.size < 28) return null
                    val salt = data.copyOfRange(0, 16)
                    val iv = data.copyOfRange(16, 28)
                    val ciphertext = data.copyOfRange(28, data.size)
                    CryptoManager.decrypt(
                        CryptoManager.EncryptedData(salt, iv, ciphertext),
                        pass,
                    )
                }
            } catch (e: Exception) {
                return null
            } finally {
                pass.fill(0.toChar())
            }
            val id = java.util.UUID.randomUUID().toString()
            val entry = DocumentEntry(
                id = id,
                type = type,
                title = title,
                encryptedFileName = "doc_$id.enc",
            )
            return addDocument(imageBytes, null, entry)
        }
    }

    // ── Internals ──

    private fun getSalt(): ByteArray? = prefs.getString(KEY_SALT, null)?.let { decodeB64(it) }

    private fun getIndex(): List<DocumentEntry> {
        val mk = masterKey ?: error("Documents vault is locked")
        val salt = getSalt() ?: ByteArray(0)

        // Try main index first
        val mainIv = prefs.getString(KEY_INDEX_IV, null)
        val mainData = prefs.getString(KEY_INDEX_DATA, null)

        if (mainIv != null && mainData != null) {
            try {
                val iv = decodeB64(mainIv)
                val ciphertext = decodeB64(mainData)
                val plaintext = CryptoManager.decryptWithKey(
                    CryptoManager.EncryptedData(salt, iv, ciphertext),
                    mk,
                )
                return json.decodeFromString<List<DocumentEntry>>(String(plaintext, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.w("DocumentStore", "Main index decrypt failed, trying backup", e)
            }
        }

        // Try backup
        val backupIv = prefs.getString(KEY_INDEX_IV_BACKUP, null)
        val backupData = prefs.getString(KEY_INDEX_DATA_BACKUP, null)
        if (backupIv != null && backupData != null) {
            try {
                val iv = decodeB64(backupIv)
                val ciphertext = decodeB64(backupData)
                val plaintext = CryptoManager.decryptWithKey(
                    CryptoManager.EncryptedData(salt, iv, ciphertext),
                    mk,
                )
                Log.w("DocumentStore", "Recovered document index from backup, restoring main")
                // Restore main from backup
                prefs.edit()
                    .putString(KEY_INDEX_IV, backupIv)
                    .putString(KEY_INDEX_DATA, backupData)
                    .commit()
                return json.decodeFromString<List<DocumentEntry>>(String(plaintext, Charsets.UTF_8))
            } catch (e: Exception) {
                Log.e("DocumentStore", "Backup index decrypt also failed — document index is inaccessible", e)
            }
        }

        // Both main and backup failed — this is a data loss situation.
        // Log loudly so the user knows their documents may be lost.
        Log.e("DocumentStore", "Document index is inaccessible (both main and backup decrypt failed). " +
            "Documents may be lost. Encrypted files may still exist in the documents directory.")
        return emptyList()
    }

    private fun saveIndex(entries: List<DocumentEntry>) {
        val mk = masterKey ?: error("Documents vault is locked")
        val plaintext = json.encodeToString(entries).toByteArray(Charsets.UTF_8)
        val encrypted = CryptoManager.encryptWithKey(plaintext, mk)

        // Backup-before-write: copy current index to backup keys
        val currentIv = prefs.getString(KEY_INDEX_IV, null)
        val currentData = prefs.getString(KEY_INDEX_DATA, null)
        if (currentIv != null && currentData != null) {
            prefs.edit()
                .putString(KEY_INDEX_IV_BACKUP, currentIv)
                .putString(KEY_INDEX_DATA_BACKUP, currentData)
                .commit()
        }

        // Write new index
        val success = prefs.edit()
            .putString(KEY_INDEX_IV, encodeB64(encrypted.iv))
            .putString(KEY_INDEX_DATA, encodeB64(encrypted.ciphertext))
            .commit()

        // On success: remove backup keys and update cache
        if (success) {
            prefs.edit()
                .remove(KEY_INDEX_IV_BACKUP)
                .remove(KEY_INDEX_DATA_BACKUP)
                .commit()
            indexCache = entries
        }
    }

    /**
     * Delete leftover camera photos from the cache directory.
     * Call from app startup to clean up temporary capture files.
     */
    fun cleanupOldCameraPhotos() {
        lock.withLock {
            val cacheDir = context.cacheDir
            cacheDir.listFiles { file -> file.name.startsWith("doc_photo_") && file.name.endsWith(".jpg") }
                ?.forEach { file ->
                    if (!file.delete()) {
                        Log.w("DocumentStore", "Failed to delete camera photo: ${file.name}")
                    }
                }
        }
    }

    /**
     * Delete leftover share files from the cache directory.
     * Call from app startup to clean up temporary export files.
     */
    fun cleanupOldShareFiles() {
        lock.withLock {
            val cacheDir = context.cacheDir
            cacheDir.listFiles { file -> file.name.startsWith("cernunnos_doc_") && file.name.endsWith(".enc") }
                ?.forEach { file ->
                    if (!file.delete()) {
                        Log.w("DocumentStore", "Failed to delete share file: ${file.name}")
                    }
                }
        }
    }

    private fun encodeB64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decodeB64(str: String): ByteArray = Base64.decode(str, Base64.NO_WRAP)

    companion object {
        private const val PREFS_NAME = "cernunnos_documents"
        private const val KEY_SETUP = "doc_setup"
        private const val KEY_SALT = "doc_salt"
        private const val KEY_INDEX_IV = "doc_index_iv"
        private const val KEY_INDEX_DATA = "doc_index_data"
        private const val KEY_INDEX_IV_BACKUP = "doc_index_iv_backup"
        private const val KEY_INDEX_DATA_BACKUP = "doc_index_data_backup"
        /** Maximum size of a single encrypted document file (32 MB). */
        private const val MAX_DOC_FILE_BYTES = 32L * 1024 * 1024
    }
}
