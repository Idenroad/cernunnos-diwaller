package com.cernunnos.authenticator.cloud

import android.content.Context
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.IOUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dropbox cloud provider.
 * Uses a long-lived access token that the user generates from:
 * https://www.dropbox.com/developers/apps → create app → generate token
 *
 * API docs: https://www.dropbox.com/developers/documentation/http/documentation
 */
class DropboxProvider(private val context: Context) : CloudProvider {

    override val id = "dropbox"
    override val displayName = "Dropbox"

    private val prefs = AppPreferences(context)
    private val oauth = DropboxOAuthManager(context)
    private val apiBase = "https://api.dropboxapi.com/2"
    private val contentBase = "https://content.dropboxapi.com/2"

    private fun getToken(): String? = oauth.getValidAccessToken()

    override fun isAuthenticated(): Boolean = prefs.dropboxToken != null

    override fun authenticate(): Boolean {
        val token = getToken() ?: return false
        return try {
            val url = URL("$apiBase/users/get_current_account")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_POST
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            val ok = conn.responseCode == CloudConfig.HTTP_OK
            conn.disconnect()
            ok
        } catch (e: Exception) {
            CloudNet.logError("DropboxProvider", "authenticate", e)
            false
        }
    }

    override fun upload(path: String, data: ByteArray): Boolean {
        val token = getToken() ?: return false
        val dropboxPath = "/${AppConfig.CLOUD_FOLDER_NAME}/${path.substringAfterLast("/")}"
        val tmpPath = "$dropboxPath${CloudConfig.TEMP_FILE_EXTENSION}"

        // Step 1: upload to .tmp (wrapped in retry)
        val uploaded = CloudNet.retry("DropboxProvider", "upload($path).tmp") {
            try {
                val url = URL("$contentBase/files/upload")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_POST
                conn.doOutput = true
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
                conn.setRequestProperty(CloudConfig.HEADER_DROPBOX_API_ARG, JSONObject().put("path", tmpPath).put("mode", "overwrite").put("mute", true).toString())
                conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_OCTET_STREAM)
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                conn.outputStream.use { it.write(data) }
                val ok = conn.responseCode == CloudConfig.HTTP_OK
                conn.disconnect()
                if (ok) true else null
            } catch (e: Exception) {
                CloudNet.logError("DropboxProvider", "upload.tmp", e)
                null
            }
        } != null

        if (!uploaded) {
            // Clean up partial .tmp
            try { deleteFile(token, tmpPath) } catch (e: Exception) { CloudNet.logError("DropboxProvider", "cleanup.tmp", e) }
            return false
        }

        // Step 2: delete old file (if exists), then rename .tmp -> final
        return try {
            deleteFile(token, dropboxPath)
            moveFile(token, tmpPath, dropboxPath)
            true
        } catch (e: Exception) {
            CloudNet.logError("DropboxProvider", "upload.rename", e)
            false
        }
    }

    private fun deleteFile(token: String, path: String) {
        val url = URL("$apiBase/files/delete_v2")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_POST
        conn.doOutput = true
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
        conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_JSON)
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        val body = JSONObject().put("path", path).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        // 200 OK on success; ignore errors (file may not exist)
        conn.responseCode
        conn.disconnect()
    }

    private fun moveFile(token: String, fromPath: String, toPath: String) {
        val url = URL("$apiBase/files/move_v2")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_POST
        conn.doOutput = true
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
        conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_JSON)
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        val body = JSONObject().put("from_path", fromPath).put("to_path", toPath).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        if (code != CloudConfig.HTTP_OK) error("Dropbox move failed: HTTP $code")
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
        val token = getToken() ?: return null
        return CloudNet.retry("DropboxProvider", "download($fileName)") {
            try {
                val dropboxPath = "/${AppConfig.CLOUD_FOLDER_NAME}/$fileName"
                val url = URL("$contentBase/files/download")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_POST
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
                conn.setRequestProperty(CloudConfig.HEADER_DROPBOX_API_ARG, JSONObject().put("path", dropboxPath).toString())
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                if (conn.responseCode != CloudConfig.HTTP_OK) {
                    conn.disconnect()
                    return@retry null
                }

                val data = conn.inputStream.use { IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES) }
                conn.disconnect()
                if (data.size < 16) {
                    android.util.Log.w("DropboxProvider", "download($fileName): file too small (${data.size} bytes)")
                    null
                } else data
            } catch (e: Exception) {
                CloudNet.logError("DropboxProvider", "download", e)
                null
            }
        }
    }

    override fun listBackups(): List<CloudFile> {
        val token = getToken() ?: return emptyList()
        return try {
            val url = URL("$apiBase/files/list_folder")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_POST
            conn.doOutput = true
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_JSON)
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            val body = JSONObject().put("path", "/${AppConfig.CLOUD_FOLDER_NAME}").toString()
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                android.util.Log.w("DropboxProvider", "listBackups: HTTP ${conn.responseCode}")
                conn.disconnect()
                return emptyList()
            }

            val response = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(response)
            val entries = json.optJSONArray("entries") ?: return emptyList()

            val files = mutableListOf<CloudFile>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (name.startsWith(BackupConfig.BACKUP_FILE_PREFIX)) {
                    val serverModified = item.optString("server_modified")
                    val modifiedMs = parseIsoDate(serverModified)
                    files.add(CloudFile(
                        name = name,
                        modified = modifiedMs,
                        size = item.optLong("size"),
                    ))
                }
            }

            files.sortedByDescending { it.modified }
        } catch (e: Exception) {
            CloudNet.logError("DropboxProvider", "listBackups", e)
            emptyList()
        }
    }

    private fun parseIsoDate(iso: String): Long {
        return try {
            val clean = iso.replace("Z", "+0000")
            val sdf = java.text.SimpleDateFormat(DateFormatConfig.DATE_FORMAT_ISO, DateFormatConfig.LOCALE)
            sdf.parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun deleteBackup(name: String): Boolean {
        val token = getToken() ?: return false
        return try {
            val dropboxPath = "/${AppConfig.CLOUD_FOLDER_NAME}/$name"
            val url = URL("$apiBase/files/delete_v2")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_POST
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(JSONObject().put("path", dropboxPath).toString().toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            code in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX
        } catch (e: Exception) {
            CloudNet.logError("DropboxProvider", "delete($name)", e)
            false
        }
    }

    override fun logout() {
        oauth.logout()
    }
}
