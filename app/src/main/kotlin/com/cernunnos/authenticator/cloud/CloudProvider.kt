package com.cernunnos.authenticator.cloud

/**
 * Universal cloud provider interface.
 * Each provider (Dropbox, Google Drive, WebDAV, SFTP) implements this.
 */
interface CloudProvider {
    /** Provider identifier */
    val id: String

    /** Authenticate with stored credentials. Returns true on success. */
    fun authenticate(): Boolean

    /** Check if we have stored credentials */
    fun isAuthenticated(): Boolean

    /** Upload encrypted data to a file path. Returns true on success. */
    fun upload(path: String, data: ByteArray): Boolean

    /** Download the most recent backup file. Returns file content or null. */
    fun downloadLatest(): ByteArray?

    /** Download a specific backup file by name. Returns file content or null. */
    fun downloadLatestByName(name: String): ByteArray? = downloadLatest()

    /** List backup files, newest first */
    fun listBackups(): List<CloudFile>

    /** Delete a backup file by name. Returns true on success. */
    fun deleteBackup(name: String): Boolean = false

    /** Clear stored credentials */
    fun logout()

    /** Display name */
    val displayName: String
}

data class CloudFile(
    val name: String,
    val modified: Long,
    val size: Long,
)
