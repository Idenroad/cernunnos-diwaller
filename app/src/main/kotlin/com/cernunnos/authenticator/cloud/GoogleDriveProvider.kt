package com.cernunnos.authenticator.cloud

import android.content.Context
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.IOUtils
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Drive cloud provider.
 * Uses an access token that the user generates from Google OAuth Playground:
 * https://developers.google.com/oauthplayground/
 *
 * Scope needed: https://www.googleapis.com/auth/drive.file
 *
 * API docs: https://developers.google.com/drive/api/guides/reference/rest
 */
class GoogleDriveProvider(private val context: Context) : CloudProvider {

    override val id = "gdrive"
    override val displayName = "Google Drive"

    private val prefs = AppPreferences(context)
    private val oauth = GoogleOAuthManager(context)
    private val uploadBase = "https://www.googleapis.com/upload/drive/v3/files"
    private val apiBase = "https://www.googleapis.com/drive/v3/files"

    private fun getToken(): String? = oauth.getValidAccessToken()

    override fun isAuthenticated(): Boolean = prefs.gdriveToken != null

    override fun authenticate(): Boolean {
        val token = getToken() ?: return false
        return try {
            val url = URL("$apiBase?pageSize=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_GET
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            val ok = conn.responseCode == CloudConfig.HTTP_OK
            conn.disconnect()
            ok
        } catch (e: Exception) {
            CloudNet.logError("GDriveProvider", "authenticate", e)
            false
        }
    }

    private fun findOrCreateFolder(token: String): String? {
        return try {
            // Search for "Cernunnos" folder
            val query = URLEncoder.encode("name='${AppConfig.CLOUD_FOLDER_NAME}' and mimeType='${CloudConfig.CONTENT_TYPE_GDRIVE_FOLDER}' and trashed=false", CloudConfig.CHARSET_UTF8)
            val url = URL("$apiBase?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_GET
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val body = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                // If multiple folders exist (race condition), use the first one.
                // The duplicates are harmless — backups will go to the first match.
                return files.optJSONObject(0)?.optString("id")
            }

            // Create folder
            val createUrl = URL(apiBase)
            val createConn = createUrl.openConnection() as HttpURLConnection
            createConn.requestMethod = CloudConfig.METHOD_POST
            createConn.doOutput = true
            createConn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            createConn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_JSON)
            createConn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            createConn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            val folderMeta = JSONObject()
                .put("name", AppConfig.CLOUD_FOLDER_NAME)
                .put("mimeType", CloudConfig.CONTENT_TYPE_GDRIVE_FOLDER)
                .toString()
            createConn.outputStream.use { it.write(folderMeta.toByteArray()) }

            if (createConn.responseCode != CloudConfig.HTTP_OK) {
                createConn.disconnect()
                return null
            }

            val createBody = createConn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            createConn.disconnect()

            JSONObject(createBody).optString("id").takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun findFileId(token: String, fileName: String): String? {
        return try {
            val query = URLEncoder.encode("name='$fileName' and trashed=false", CloudConfig.CHARSET_UTF8)
            val url = URL("$apiBase?q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_GET
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val body = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                return files.optJSONObject(0)?.optString("id")
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun upload(path: String, data: ByteArray): Boolean {
        val token = getToken() ?: return false
        val fileName = path.substringAfterLast("/")
        val tmpName = "$fileName${CloudConfig.TEMP_FILE_EXTENSION}"

        // Step 1: upload to .tmp file (wrapped in retry)
        val tmpFileId = CloudNet.retry("GDriveProvider", "upload($path).tmp") {
            try {
                val folderId = findOrCreateFolder(token) ?: return@retry null
                val metadata = JSONObject().put("name", tmpName).put("parents", JSONArray().put(folderId))
                val boundary = "----CernunnosBoundary${System.currentTimeMillis()}"

                val uploadUrl = URL("$uploadBase?uploadType=multipart")
                val conn = uploadUrl.openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_POST
                conn.doOutput = true
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
                conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, "${CloudConfig.CONTENT_TYPE_MULTIPART_RELATED}; boundary=$boundary")
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                conn.outputStream.use { out ->
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("${CloudConfig.HEADER_CONTENT_TYPE}: ${CloudConfig.CONTENT_TYPE_JSON}; charset=${CloudConfig.CHARSET_UTF8}\r\n\r\n".toByteArray())
                    out.write(metadata.toString().toByteArray())
                    out.write("\r\n".toByteArray())
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("${CloudConfig.HEADER_CONTENT_TYPE}: ${CloudConfig.CONTENT_TYPE_OCTET_STREAM}\r\n\r\n".toByteArray())
                    out.write(data)
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                    out.flush()
                }

                val ok = conn.responseCode in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX
                if (!ok) {
                    conn.disconnect()
                    return@retry null
                }
                val respBody = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
                conn.disconnect()
                JSONObject(respBody).optString("id").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                CloudNet.logError("GDriveProvider", "upload.tmp", e)
                null
            }
        }

        if (tmpFileId == null) {
            return false
        }

        // Step 2: delete old file (if exists), then rename .tmp -> final
        return try {
            val existingId = findFileId(token, fileName)
            if (existingId != null) {
                deleteFile(token, existingId)
            }
            renameFile(token, tmpFileId, fileName)
            true
        } catch (e: Exception) {
            CloudNet.logError("GDriveProvider", "upload.rename", e)
            // Clean up orphaned .tmp
            try { deleteFile(token, tmpFileId) } catch (e: Exception) { CloudNet.logError("GDriveProvider", "cleanup.tmp", e) }
            false
        }
    }

    private fun deleteFile(token: String, fileId: String) {
        val url = URL("$apiBase/$fileId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_DELETE
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.responseCode
        conn.disconnect()
    }

    private fun renameFile(token: String, fileId: String, newName: String) {
        val url = URL("$apiBase/$fileId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_PATCH
        conn.doOutput = true
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
        conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_JSON)
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        val body = JSONObject().put("name", newName).toString()
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX) error("GDrive rename failed: HTTP $code")
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
        return CloudNet.retry("GDriveProvider", "download($fileName)") {
            try {
                val fileId = findFileId(token, fileName) ?: return@retry null
                val url = URL("$apiBase/$fileId?alt=media")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_GET
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                if (conn.responseCode != CloudConfig.HTTP_OK) {
                    conn.disconnect()
                    return@retry null
                }

                val data = conn.inputStream.use { IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES) }
                conn.disconnect()
                if (data.size < 16) {
                    android.util.Log.w("GDriveProvider", "download($fileName): file too small (${data.size} bytes)")
                    null
                } else data
            } catch (e: Exception) {
                CloudNet.logError("GDriveProvider", "download", e)
                null
            }
        }
    }

    override fun listBackups(): List<CloudFile> {
        val token = getToken() ?: return emptyList()
        return try {
            val query = URLEncoder.encode("name contains '${BackupConfig.BACKUP_FILE_PREFIX}' and trashed=false", CloudConfig.CHARSET_UTF8)
            val url = URL("$apiBase?q=$query&orderBy=modifiedTime desc&pageSize=20")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_GET
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                android.util.Log.w("GDriveProvider", "listBackups: HTTP ${conn.responseCode}")
                conn.disconnect()
                return emptyList()
            }

            val body = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: return emptyList()

            val result = mutableListOf<CloudFile>()
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val modifiedTime = item.optString("modifiedTime")
                val modifiedMs = parseIsoDate(modifiedTime)
                result.add(CloudFile(
                    name = name,
                    modified = modifiedMs,
                    size = item.optLong("size"),
                ))
            }

            result.sortedByDescending { it.modified }
        } catch (e: Exception) {
            CloudNet.logError("GDriveProvider", "listBackups", e)
            emptyList()
        }
    }

    private fun parseIsoDate(iso: String): Long {
        return try {
            val clean = iso.replace("Z", "+0000")
            val sdf = java.text.SimpleDateFormat(DateFormatConfig.DATE_FORMAT_ISO_WITH_MILLIS, DateFormatConfig.LOCALE)
            sdf.parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val clean = iso.replace("Z", "+0000")
                val sdf = java.text.SimpleDateFormat(DateFormatConfig.DATE_FORMAT_ISO, DateFormatConfig.LOCALE)
                sdf.parse(clean)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    override fun deleteBackup(name: String): Boolean {
        val token = getToken() ?: return false
        return try {
            val fileId = findFileId(token, name) ?: return false
            val url = URL("$apiBase/$fileId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, "${CloudConfig.AUTH_BEARER_PREFIX}$token")
            conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()
            val code = conn.responseCode
            conn.disconnect()
            code in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX
        } catch (e: Exception) {
            CloudNet.logError("GDriveProvider", "delete($name)", e)
            false
        }
    }

    override fun logout() {
        oauth.logout()
    }
}
