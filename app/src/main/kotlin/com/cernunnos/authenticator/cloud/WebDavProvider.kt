package com.cernunnos.authenticator.cloud

import android.content.Context
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.IOUtils
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * WebDAV cloud provider.
 * Works with Nextcloud, ownCloud, Synology, Proton Drive, any WebDAV server.
 *
 * User provides: server URL, username, password.
 * Uses Basic Auth.
 */
class WebDavProvider(private val context: Context) : CloudProvider {

    override val id = "webdav"
    override val displayName = "WebDAV"

    private val prefs = AppPreferences(context)

    private fun getUrl(): String? = prefs.webdavUrl
    private fun getUser(): String? = prefs.webdavUser
    private fun getPass(): String? = prefs.webdavPass

    fun setCredentials(url: String, username: String, password: String) {
        // Validate and normalize URL via shared pure-JVM utility
        val normalized = WebDavValidator.validateUrl(url)
        prefs.webdavUrl = normalized
        prefs.webdavUser = username
        prefs.webdavPass = password
    }

    private fun authHeader(): String? {
        val user = getUser() ?: return null
        val pass = getPass() ?: return null
        val credentials = "$user:$pass"
        val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }

    override fun isAuthenticated(): Boolean {
        return getUrl() != null && getUser() != null && getPass() != null
    }

    override fun authenticate(): Boolean {
        val url = getUrl() ?: return false
        val auth = authHeader() ?: return false
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_PROPFIND
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
            conn.setRequestProperty(CloudConfig.HEADER_DEPTH, "0")
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            val code = conn.responseCode
            conn.disconnect()
            when (code) {
                CloudConfig.HTTP_MULTI_STATUS, CloudConfig.HTTP_OK -> true
                CloudConfig.HTTP_UNAUTHORIZED -> {
                    android.util.Log.e("WebDavProvider", "Authentication failed: invalid credentials (HTTP 401)")
                    false
                }
                else -> {
                    android.util.Log.w("WebDavProvider", "Authentication failed: HTTP $code")
                    false
                }
            }
        } catch (e: Exception) {
            CloudNet.logError("WebDavProvider", "authenticate", e)
            false
        }
    }

    private fun ensureFolder(folderPath: String) {
        val baseUrl = getUrl() ?: return
        val auth = authHeader() ?: return
        try {
            val url = URL(baseUrl + folderPath.trimStart('/'))
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_MKCOL
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
            conn.connectTimeout = CloudConfig.TIMEOUT_SHORT.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_SHORT.toInt()
            // 201 Created or 405 Method Not Allowed (already exists) are both OK
            val code = conn.responseCode
            conn.disconnect()
            // Log non-expected codes for diagnostics (401 = auth issue, 5xx = server error)
            if (code != 201 && code != 405 && code != 301) {
                android.util.Log.w("WebDavProvider", "ensureFolder($folderPath): HTTP $code (201/405 expected)")
            }
        } catch (e: Exception) {
            // Log the error — folder might already exist, but the user should know
            // if there's a network or auth issue
            android.util.Log.w("WebDavProvider", "ensureFolder($folderPath) failed: ${e.message}")
        }
    }

    override fun upload(path: String, data: ByteArray): Boolean {
        val baseUrl = getUrl() ?: return false
        val auth = authHeader() ?: return false
        val fileName = path.substringAfterLast("/")
        val fullUrl = baseUrl + AppConfig.CLOUD_FOLDER_NAME + "/" + fileName
        val tmpUrl = "$fullUrl${CloudConfig.TEMP_FILE_EXTENSION}"

        // Step 1: upload to .tmp (wrapped in retry)
        val uploaded = CloudNet.retry("WebDavProvider", "upload($path).tmp") {
            try {
                // Ensure Cernunnos folder exists
                ensureFolder(AppConfig.CLOUD_FOLDER_NAME)

                val conn = URL(tmpUrl).openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_PUT
                conn.doOutput = true
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
                conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_OCTET_STREAM)
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                conn.outputStream.use { it.write(data) }
                val ok = conn.responseCode in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX
                conn.disconnect()
                if (ok) true else null
            } catch (e: Exception) {
                CloudNet.logError("WebDavProvider", "upload.tmp", e)
                null
            }
        } != null

        if (!uploaded) {
            // Clean up partial .tmp
            try { deleteWebDav(auth, tmpUrl) } catch (e: Exception) { CloudNet.logError("WebDavProvider", "cleanup.tmp", e) }
            return false
        }

        // Step 2: delete old file (if exists), then MOVE .tmp -> final
        return try {
            deleteWebDav(auth, fullUrl)
            moveWebDav(auth, tmpUrl, fullUrl)
            true
        } catch (e: Exception) {
            CloudNet.logError("WebDavProvider", "upload.rename", e)
            false
        }
    }

    private fun deleteWebDav(auth: String, url: String) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_DELETE
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.responseCode
        conn.disconnect()
    }

    private fun moveWebDav(auth: String, fromUrl: String, toUrl: String) {
        val conn = URL(fromUrl).openConnection() as HttpURLConnection
        conn.requestMethod = CloudConfig.METHOD_MOVE
        conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
        conn.setRequestProperty(CloudConfig.HEADER_DESTINATION, toUrl)
        conn.setRequestProperty(CloudConfig.HEADER_OVERWRITE, "T")
        conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
        val code = conn.responseCode
        conn.disconnect()
        if (code !in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX) error("WebDAV MOVE failed: HTTP $code")
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
        val baseUrl = getUrl() ?: return null
        val auth = authHeader() ?: return null
        return CloudNet.retry("WebDavProvider", "download($fileName)") {
            try {
                val fullUrl = baseUrl + AppConfig.CLOUD_FOLDER_NAME + "/" + fileName
                val conn = URL(fullUrl).openConnection() as HttpURLConnection
                conn.requestMethod = CloudConfig.METHOD_GET
                conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
                conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
                conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()

                if (conn.responseCode != CloudConfig.HTTP_OK) {
                    conn.disconnect()
                    return@retry null
                }

                val data = conn.inputStream.use { IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES) }
                conn.disconnect()
                if (data.size < 16) {
                    android.util.Log.w("WebDavProvider", "download($fileName): file too small (${data.size} bytes)")
                    null
                } else data
            } catch (e: Exception) {
                CloudNet.logError("WebDavProvider", "download", e)
                null
            }
        }
    }

    override fun listBackups(): List<CloudFile> {
        val baseUrl = getUrl() ?: return emptyList()
        val auth = authHeader() ?: return emptyList()
        return try {
            val fullUrl = baseUrl + AppConfig.CLOUD_FOLDER_NAME + "/"
            val conn = URL(fullUrl).openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_PROPFIND
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
            conn.setRequestProperty(CloudConfig.HEADER_DEPTH, "1")
            conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_XML)
            conn.doOutput = true
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            // Empty PROPFIND body (request all properties)
            val propfindBody = """<?xml version="1.0" encoding="utf-8"?>
                |<propfind xmlns="DAV:">
                |  <prop>
                |    <displayname/>
                |    <getlastmodified/>
                |    <getcontentlength/>
                |  </prop>
                |</propfind>""".trimMargin()
            conn.outputStream.use { it.write(propfindBody.toByteArray()) }

            if (conn.responseCode != CloudConfig.HTTP_MULTI_STATUS) {
                android.util.Log.w("WebDavProvider", "listBackups: HTTP ${conn.responseCode}")
                conn.disconnect()
                return emptyList()
            }

            val body = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            // Parse XML responses (regex-based parsing that accepts XML namespaces).
            // Some WebDAV servers use namespace prefixes like <D:response> or <d:response>.
            // The regexes below accept an optional prefix before the element name.
            val files = mutableListOf<CloudFile>()
            val responseRegex = Regex("""<(?:\w+:)?response>(.*?)</(?:\w+:)?response>""", RegexOption.DOT_MATCHES_ALL)
            val hrefRegex = Regex("""<(?:\w+:)?href>(.*?)</(?:\w+:)?href>""")
            val modifiedRegex = Regex("""<(?:\w+:)?getlastmodified>(.*?)</(?:\w+:)?getlastmodified>""")
            val sizeRegex = Regex("""<(?:\w+:)?getcontentlength>(.*?)</(?:\w+:)?getcontentlength>""")

            for (match in responseRegex.findAll(body)) {
                val responseXml = match.groupValues[1]
                val href = hrefRegex.find(responseXml)?.groupValues?.get(1) ?: continue
                val name = href.substringAfterLast('/').decodeURL()
                if (!name.startsWith(BackupConfig.BACKUP_FILE_PREFIX)) continue

                val modifiedStr = modifiedRegex.find(responseXml)?.groupValues?.get(1) ?: ""
                val modifiedMs = parseHttpDate(modifiedStr)
                val size = sizeRegex.find(responseXml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                files.add(CloudFile(name = name, modified = modifiedMs, size = size))
            }

            files.sortedByDescending { it.modified }
        } catch (e: Exception) {
            CloudNet.logError("WebDavProvider", "listBackups", e)
            emptyList()
        }
    }

    private fun String.decodeURL(): String {
        return try {
            java.net.URLDecoder.decode(this, CloudConfig.CHARSET_UTF8)
        } catch (e: Exception) {
            this
        }
    }

    private fun parseHttpDate(date: String): Long {
        return try {
            // RFC 1123: "Sun, 06 Nov 1994 08:49:37 GMT"
            val sdf = java.text.SimpleDateFormat(DateFormatConfig.DATE_FORMAT_RFC1123, DateFormatConfig.LOCALE)
            sdf.parse(date)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun deleteBackup(name: String): Boolean {
        val baseUrl = getUrl() ?: return false
        val auth = authHeader() ?: return false
        return try {
            val fullUrl = baseUrl + AppConfig.CLOUD_FOLDER_NAME + "/" + name
            val conn = URL(fullUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty(CloudConfig.HEADER_AUTHORIZATION, auth)
            conn.connectTimeout = CloudConfig.TIMEOUT_UPLOAD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_DOWNLOAD.toInt()
            val code = conn.responseCode
            conn.disconnect()
            code in CloudConfig.HTTP_SUCCESS_MIN..CloudConfig.HTTP_SUCCESS_MAX
        } catch (e: Exception) {
            CloudNet.logError("WebDavProvider", "delete($name)", e)
            false
        }
    }

    override fun logout() {
        prefs.webdavUrl = null
        prefs.webdavUser = null
        prefs.webdavPass = null
    }
}
