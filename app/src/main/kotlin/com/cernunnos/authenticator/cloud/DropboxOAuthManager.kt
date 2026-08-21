package com.cernunnos.authenticator.cloud

import android.content.Context
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.util.IOUtils
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Dropbox OAuth 2.0 token manager.
 *
 * Authorization is handled by [AppAuthManager] (PKCE + AppAuth).
 * This class only manages token storage, refresh, and validation.
 */
class DropboxOAuthManager(private val context: Context) {

    private val prefs = AppPreferences(context)

    companion object {
        private const val TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"
    }

    fun getAppKey(): String {
        val custom = prefs.dropboxAppKey
        return if (!custom.isNullOrBlank()) custom else CloudKeys.DROPBOX_APP_KEY
    }

    fun getAccessToken(): String? = prefs.dropboxToken

    /**
     * Refresh the access token using the stored refresh token.
     */
    fun refreshAccessToken(): String? {
        val refreshToken = prefs.dropboxRefreshToken ?: return null
        val appKey = getAppKey()
        if (appKey.startsWith("REPLACE_")) return null
        return try {
            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_POST
            conn.doOutput = true
            conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_FORM_URLENCODED)
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            val body = "refresh_token=${URLEncoder.encode(refreshToken, CloudConfig.CHARSET_UTF8)}" +
                "&grant_type=refresh_token" +
                "&client_id=${URLEncoder.encode(appKey, CloudConfig.CHARSET_UTF8)}"

            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(response)
            val accessToken = json.optString("access_token")
            val expiresIn = json.optLong("expires_in", 14400)

            if (accessToken.isEmpty()) return null

            prefs.dropboxToken = accessToken
            prefs.dropboxTokenExpiry = System.currentTimeMillis() + (expiresIn * 1000)

            accessToken
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a valid access token, refreshing if necessary.
     */
    fun getValidAccessToken(): String? {
        val token = getAccessToken() ?: return null
        val expiry = prefs.dropboxTokenExpiry
        if (System.currentTimeMillis() > expiry - 300_000) {
            val refreshed = refreshAccessToken()
            if (refreshed != null) return refreshed
            // Refresh failed — token is expired, force re-auth
            android.util.Log.w("DropboxOAuth", "Refresh failed — session expired, user must re-authenticate")
            logout()
            return null
        }
        return token
    }

    fun logout() {
        prefs.dropboxToken = null
        prefs.dropboxRefreshToken = null
        prefs.dropboxAppKey = null
        prefs.dropboxTokenExpiry = 0
    }
}
