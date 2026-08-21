package com.cernunnos.authenticator.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cernunnos.authenticator.R
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.data.model.DocumentType
import com.cernunnos.authenticator.data.repo.DocumentRepository
import com.cernunnos.authenticator.data.storage.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DocumentVaultState {
    UNINITIALIZED,  // Vault not created yet
    LOCKED,         // Vault exists but locked
    UNLOCKED,       // Vault unlocked and accessible
}

data class DocumentUiState(
    val vaultState: DocumentVaultState = DocumentVaultState.UNINITIALIZED,
    val documents: List<DocumentEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val expiringSoon: List<DocumentEntry> = emptyList(),
    val expired: List<DocumentEntry> = emptyList(),
)

class DocumentViewModel(app: Application) : AndroidViewModel(app) {

    private val store = DocumentStore(app)
    private val repo = DocumentRepository(store)

    private val _uiState = MutableStateFlow(
        DocumentUiState(
            vaultState = if (store.isInitialized) DocumentVaultState.LOCKED else DocumentVaultState.UNINITIALIZED,
        ),
    )
    val uiState: StateFlow<DocumentUiState> = _uiState.asStateFlow()

    // ── Vault lifecycle ──

    fun initializeVault(passphrase: String) {
        if (passphrase.length < 8) {
            _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_pass_too_short))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pass = passphrase.toCharArray()
            try {
                store.initialize(pass)
                refreshState()
                _uiState.value = _uiState.value.copy(error = null, message = getApplication<Application>().getString(R.string.doc_msg_vault_created))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_create_vault, e.message))
            } finally {
                pass.fill(0.toChar())
            }
        }
    }

    fun unlock(passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pass = passphrase.toCharArray()
            val success = try {
                store.unlock(pass)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_wrong_pass))
                false
            } finally {
                pass.fill(0.toChar())
            }
            if (success) {
                refreshState()
                _uiState.value = _uiState.value.copy(error = null, message = null)
            } else {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_wrong_pass))
            }
        }
    }

    fun lock() {
        store.lock()
        _uiState.value = _uiState.value.copy(
            vaultState = DocumentVaultState.LOCKED,
            documents = emptyList(),
            expiringSoon = emptyList(),
            expired = emptyList(),
        )
    }

    // ── CRUD ──

    fun addDocument(rectoBitmap: Bitmap, versoBitmap: Bitmap?, type: DocumentType, title: String, expirationDate: Long?, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.addDocument(rectoBitmap, versoBitmap, type, title, expirationDate, notes)
                refreshState()
                // Clean up camera capture temp files now that the document is safely stored.
                // The app may stay in background for weeks/months without restart, so relying
                // on startup cleanup alone would let temp photos accumulate in cacheDir
                // (potentially hundreds of MB). Cleanup here is cheap and bounded.
                cleanupCameraTempFiles()
                _uiState.value = _uiState.value.copy(error = null, message = getApplication<Application>().getString(R.string.doc_msg_added, title))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_add_failed, e.message))
            }
        }
    }

    /**
     * Delete leftover camera capture photos from the cache directory.
     * Called after a successful addDocument to prevent unbounded accumulation
     * of temp files between app restarts (phones rarely reboot, and the app
     * stays in background for weeks).
     */
    private fun cleanupCameraTempFiles() {
        try {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles { f ->
                (f.name.startsWith("doc_photo_") && f.name.endsWith(".jpg")) ||
                    (f.name.startsWith("photo_") && f.name.endsWith(".jpg"))
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            android.util.Log.w("DocumentViewModel", "camera temp cleanup failed: ${e.message}")
        }
    }

    fun updateDocument(entry: DocumentEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.updateDocument(entry)
                refreshState()
                _uiState.value = _uiState.value.copy(error = null, message = getApplication<Application>().getString(R.string.doc_msg_updated))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_update_failed, e.message))
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteDocument(id)
                refreshState()
                _uiState.value = _uiState.value.copy(error = null, message = getApplication<Application>().getString(R.string.doc_msg_deleted))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_delete_failed, e.message))
            }
        }
    }

    fun getDocumentImage(entry: DocumentEntry, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = repo.getDocumentImage(entry)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                onResult(bitmap)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_load_image, e.message))
                onResult(null)
            }
        }
    }

    fun getDocumentVersoImage(entry: DocumentEntry, onResult: (Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = repo.getDocumentVersoImage(entry)
                if (bytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    onResult(bitmap)
                } else {
                    onResult(null)
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    // ── Sharing ──

    fun exportDocument(id: String, passphrase: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val data = repo.exportDocument(id, passphrase)
                if (data == null) {
                    _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_export_failed))
                }
                onResult(data)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_export_failed))
                onResult(null)
            }
        }
    }

    fun importDocument(data: ByteArray, passphrase: String, title: String, type: DocumentType, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = repo.importDocument(data, passphrase, title, type)
                if (entry != null) {
                    refreshState()
                    _uiState.value = _uiState.value.copy(error = null, message = getApplication<Application>().getString(R.string.doc_msg_imported, title))
                    onResult(true)
                } else {
                    _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_import_failed))
                    onResult(false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.doc_err_import_failed))
                onResult(false)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }

    // ── Internals ──

    private fun refreshState() {
        val docs = if (store.isUnlocked) repo.getDocuments() else emptyList()
        val expiring = if (store.isUnlocked) repo.getExpiringDocuments(30) else emptyList()
        val expired = if (store.isUnlocked) repo.getExpiredDocuments() else emptyList()
        _uiState.value = _uiState.value.copy(
            vaultState = DocumentVaultState.UNLOCKED,
            documents = docs,
            expiringSoon = expiring,
            expired = expired,
        )
    }

    override fun onCleared() {
        super.onCleared()
        store.lock()
    }
}
