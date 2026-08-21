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
 * Google OAuth 2.0 token manager for Google Drive.
 *
 * Authorization is handled by [AppAuthManager] (PKCE + AppAuth).
 * This class only manages token storage, refresh, and validation.
 */
class GoogleOAuthManager(private val context: Context) {

    private val prefs = AppPreferences(context)

    companion object {
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }

    fun getClientId(): String {
        // Use embedded key by default, allow override via prefs
        val custom = prefs.gdriveClientId
        return if (!custom.isNullOrBlank()) custom else CloudKeys.GOOGLE_CLIENT_ID
    }

    fun getAccessToken(): String? = prefs.gdriveToken
    fun getRefreshToken(): String? = prefs.gdriveRefreshToken

    /**
     * Refresh the access token using the stored refresh token.
     * Returns the new access token or null.
     */
    fun refreshAccessToken(): String? {
        val refreshToken = getRefreshToken() ?: return null
        val clientId = getClientId()
        if (clientId.startsWith("REPLACE_")) return null
        return try {
            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = CloudConfig.METHOD_POST
            conn.doOutput = true
            conn.setRequestProperty(CloudConfig.HEADER_CONTENT_TYPE, CloudConfig.CONTENT_TYPE_FORM_URLENCODED)
            conn.connectTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()
            conn.readTimeout = CloudConfig.TIMEOUT_STANDARD.toInt()

            val body = "refresh_token=${URLEncoder.encode(refreshToken, CloudConfig.CHARSET_UTF8)}" +
                "&client_id=${URLEncoder.encode(clientId, CloudConfig.CHARSET_UTF8)}" +
                "&grant_type=refresh_token"

            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode != CloudConfig.HTTP_OK) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.use { String(IOUtils.readBounded(it, IOUtils.MAX_NETWORK_BYTES), Charsets.UTF_8) }
            conn.disconnect()

            val json = JSONObject(response)
            val accessToken = json.optString("access_token")
            val expiresIn = json.optLong("expires_in", 3600)

            if (accessToken.isEmpty()) return null

            prefs.gdriveToken = accessToken
            prefs.gdriveTokenExpiry = System.currentTimeMillis() + (expiresIn * 1000)

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
        val expiry = prefs.gdriveTokenExpiry
        if (System.currentTimeMillis() > expiry - 300_000) {
            val refreshed = refreshAccessToken()
            if (refreshed != null) return refreshed
            android.util.Log.w("GoogleOAuth", "Refresh failed — session expired, user must re-authenticate")
            logout()
            return null
        }
        return token
    }

    fun logout() {
        prefs.gdriveToken = null
        prefs.gdriveRefreshToken = null
        prefs.gdriveClientId = null
        prefs.gdriveTokenExpiry = 0
    }
}
