package com.cernunnos.authenticator.cloud

import android.content.Context
import com.cernunnos.authenticator.BuildConfig
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.IOUtils
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.SftpException
import java.io.ByteArrayOutputStream
import java.util.Vector

/**
 * SFTP cloud provider.
 * Works with any SFTP/SSH server (OpenSSH, ProFTPd, vsftpd, etc.).
 *
 * User provides: host, port, username, password, remote path.
 * Uses JSch for SSH/SFTP connectivity.
 *
 * Host key pinning (MITM protection):
 * On the first successful connection the server's public host key is captured
 * and stored (Base64) in EncryptedSharedPreferences. On every subsequent
 * connection the presented host key is compared against the pinned value; a
 * mismatch aborts the connection (raises an exception) to prevent man-in-the
 * middle attacks. If the server's key legitimately changes, the user must
 * re-pair the host (logout/login clears the pinned key).
 */
class SftpProvider(private val context: Context) : CloudProvider {

    override val id = "sftp"
    override val displayName = "SFTP"

    private val prefs = AppPreferences(context)

    private fun getHost(): String? = prefs.sftpHost
    private fun getPort(): Int = prefs.sftpPort
    private fun getUser(): String? = prefs.sftpUser
    private fun getPass(): String? = prefs.sftpPass
    private fun getRemotePath(): String = prefs.sftpPath ?: AppConfig.CLOUD_FOLDER_NAME

    fun setCredentials(host: String, port: Int, username: String, password: String, remotePath: String) {
        prefs.sftpHost = host
        prefs.sftpPort = port
        prefs.sftpUser = username
        prefs.sftpPass = password
        prefs.sftpPath = remotePath.ifBlank { AppConfig.CLOUD_FOLDER_NAME }
        // Reset pinned host key whenever credentials change — forces re-pinning.
        prefs.sftpHostKey = null
    }

    override fun isAuthenticated(): Boolean {
        return getHost() != null && getUser() != null && getPass() != null
    }

    override fun authenticate(): Boolean {
        return try {
            withChannel { _, _ -> } // just connect and disconnect
            true
        } catch (e: Exception) {
            CloudNet.logError("SftpProvider", "authenticate", e)
            false
        }
    }

    /**
     * Open an SFTP channel and execute a block.
     * Ensures the channel and session are properly closed.
     *
     * Host key verification:
     * - StrictHostKeyChecking is set to "no" so JSch can complete the SSH
     *   handshake and expose the server's host key via `Session.hostKey`.
     * - After connect, the presented host key is compared to the pinned key
     *   (if any). On mismatch the session is torn down and an exception is
     *   raised. On first connection the key is pinned.
     */
    private fun <T> withChannel(block: (ChannelSftp, com.jcraft.jsch.Session) -> T): T {
        val host = getHost() ?: error("No SFTP host configured")
        val port = getPort()
        val user = getUser() ?: error("No SFTP user configured")
        val pass = getPass() ?: error("No SFTP password configured")

        val jsch = JSch()
        val session = jsch.getSession(user, host, port)
        session.setPassword(pass)
        // "no" allows the handshake to complete so we can read the host key;
        // we enforce pinning ourselves below (see verifyHostKey).
        session.setConfig("StrictHostKeyChecking", CloudConfig.SFTP_STRICT_HOST_CHECKING)
        session.setConfig("PreferredAuthentications", CloudConfig.SFTP_PREFERRED_AUTH)
        session.connect(CloudConfig.TIMEOUT_UPLOAD.toInt())

        try {
            // Verify / pin the host key immediately after connecting.
            verifyHostKey(host, session)
            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CloudConfig.TIMEOUT_UPLOAD.toInt())
            try {
                return block(channel, session)
            } finally {
                channel.disconnect()
            }
        } finally {
            session.disconnect()
        }
    }

    /**
     * Compare the host key presented by the server against the pinned value.
     * On the first connection the key is captured and pinned. On mismatch the
     * session is disconnected and a [SecurityException] is thrown to abort the
     * operation (potential MITM).
     */
    private fun verifyHostKey(host: String, session: com.jcraft.jsch.Session) {
        // session.hostKey returns a HostKey whose toString() is of the form
        // "type base64key" (e.g. "ssh-ed25519 AAAA..."). We pin the full
        // string so both the key type and the key material are verified.
        val presented = session.hostKey?.toString() ?: return
        val pinned = prefs.sftpHostKey
        if (pinned == null) {
            // First connection — pin the key.
            prefs.sftpHostKey = presented
            android.util.Log.i("SftpProvider", "Pinned host key for $host")
        } else if (pinned != presented) {
            // Key changed — possible MITM. Abort.
            session.disconnect()
            throw SecurityException(
                "SFTP host key for $host has changed — possible man-in-the-middle attack. " +
                    "If the server key legitimately changed, log out and re-enter the SFTP credentials."
            )
        }
    }

    private fun ensureRemoteDir(channel: ChannelSftp, path: String) {
        try {
            channel.cd(path)
        } catch (e: SftpException) {
            // Directory doesn't exist — create it
            try {
                // Create parent dirs recursively
                val parts = path.split("/").filter { it.isNotEmpty() }
                val sb = StringBuilder()
                for (part in parts) {
                    sb.append("/").append(part)
                    val dirPath = sb.toString()
                    try {
                        channel.cd(dirPath)
                    } catch (e2: SftpException) {
                        channel.mkdir(dirPath)
                    }
                }
                channel.cd(path)
            } catch (e3: Exception) {
                // Best effort
            }
        }
    }

    override fun upload(path: String, data: ByteArray): Boolean {
        // Step 1: upload to .tmp (wrapped in retry)
        val uploaded = CloudNet.retry("SftpProvider", "upload($path).tmp") {
            try {
                withChannel { channel, _ ->
                    val remotePath = getRemotePath()
                    ensureRemoteDir(channel, remotePath)
                    val fileName = path.substringAfterLast("/")
                    val tmpPath = "$remotePath/$fileName${CloudConfig.TEMP_FILE_EXTENSION}"
                    channel.put(data.inputStream(), tmpPath)
                    true
                }
            } catch (e: Exception) {
                CloudNet.logError("SftpProvider", "upload.tmp", e)
                null
            }
        } != null

        if (!uploaded) {
            // Clean up partial .tmp
            try {
                withChannel { channel, _ ->
                    val remotePath = getRemotePath()
                    val fileName = path.substringAfterLast("/")
                    val tmpPath = "$remotePath/$fileName${CloudConfig.TEMP_FILE_EXTENSION}"
                    channel.rm(tmpPath)
                }
            } catch (e: Exception) { CloudNet.logError("SftpProvider", "cleanup.tmp", e) }
            return false
        }

        // Step 2: delete old file (if exists), then rename .tmp -> final
        return try {
            withChannel { channel, _ ->
                val remotePath = getRemotePath()
                val fileName = path.substringAfterLast("/")
                val fullPath = "$remotePath/$fileName"
                val tmpPath = "$remotePath/$fileName${CloudConfig.TEMP_FILE_EXTENSION}"
                // Delete old file (ignore if not exists)
                try { channel.rm(fullPath) } catch (e: SftpException) { if (BuildConfig.DEBUG) android.util.Log.d("SftpProvider", "rm old: ${e.message}") }
                channel.rename(tmpPath, fullPath)
            }
            true
        } catch (e: Exception) {
            CloudNet.logError("SftpProvider", "upload.rename", e)
            false
        }
    }

    override fun downloadLatest(): ByteArray? {
        val backups = listBackups()
        if (backups.isEmpty()) return null
        return downloadFile(backups.first().name)
    }

    override fun downloadLatestByName(name: String): ByteArray? {
        return downloadFile(name)
    }

    private fun downloadFile(fileName: String): ByteArray? {
        return CloudNet.retry("SftpProvider", "download($fileName)") {
            try {
                withChannel { channel, _ ->
                    val remotePath = getRemotePath()
                    val fullPath = "$remotePath/$fileName"
                    // Bounded read to prevent OOM on corrupted/oversized remote files
                    val out = ByteArrayOutputStream()
                    channel.get(fullPath).use { input ->
                        val chunk = ByteArray(8 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(chunk)
                            if (read <= 0) break
                            total += read
                            if (total > IOUtils.MAX_NETWORK_BYTES) {
                                throw java.io.IOException("Remote file exceeds maximum allowed size (${IOUtils.MAX_NETWORK_BYTES} bytes)")
                            }
                            out.write(chunk, 0, read)
                        }
                    }
                    val data = out.toByteArray()
                    if (data.size < 16) {
                        android.util.Log.w("SftpProvider", "download($fileName): file too small (${data.size} bytes)")
                        null
                    } else data
                }
            } catch (e: Exception) {
                CloudNet.logError("SftpProvider", "download", e)
                null
            }
        }
    }

    override fun listBackups(): List<CloudFile> {
        return try {
            withChannel { channel, _ ->
                val remotePath = getRemotePath()
                val files = mutableListOf<CloudFile>()

                try {
                    @Suppress("UNCHECKED_CAST")
                    val entries = channel.ls(remotePath) as Vector<ChannelSftp.LsEntry>
                    for (entry in entries) {
                        val name = entry.filename
                        if (name.startsWith(BackupConfig.BACKUP_FILE_PREFIX) && !entry.attrs.isDir) {
                            files.add(CloudFile(
                                name = name,
                                modified = entry.attrs.mTime * 1000L,
                                size = entry.attrs.size,
                            ))
                        }
                    }
                } catch (e: SftpException) {
                    // Directory doesn't exist yet
                }

                files.sortedByDescending { it.modified }
            }
        } catch (e: Exception) {
            CloudNet.logError("SftpProvider", "listBackups", e)
            emptyList()
        }
    }

    override fun deleteBackup(name: String): Boolean {
        return try {
            withChannel { channel, _ ->
                val remotePath = getRemotePath()
                channel.rm("$remotePath/$name")
                true
            }
        } catch (e: Exception) {
            CloudNet.logError("SftpProvider", "delete($name)", e)
            false
        }
    }

    override fun logout() {
        prefs.sftpHost = null
        prefs.sftpPort = CloudConfig.SFTP_DEFAULT_PORT
        prefs.sftpUser = null
        prefs.sftpPass = null
        prefs.sftpPath = null
        prefs.sftpHostKey = null
    }
}
