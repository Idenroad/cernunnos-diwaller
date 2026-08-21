package com.cernunnos.authenticator.cloud

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.cernunnos.authenticator.BuildConfig
import com.cernunnos.authenticator.data.storage.AppPreferences
import kotlinx.coroutines.CompletableDeferred
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Unified OAuth manager using AppAuth library.
 * Handles Google Drive and Dropbox OAuth flows with proper custom scheme redirects.
 */
class AppAuthManager(private val context: Context) {

    private val prefs = AppPreferences(context)
    private val authService = AuthorizationService(context)
    private val tokenExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Cernunnos-OAuth-Token").apply { isDaemon = true }
    }

    companion object {
        private val GOOGLE_CONFIG = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )

        private val DROPBOX_CONFIG = AuthorizationServiceConfiguration(
            Uri.parse("https://www.dropbox.com/oauth2/authorize"),
            Uri.parse("https://api.dropboxapi.com/oauth2/token"),
        )

        const val REDIRECT_URI = "com.cernunnos.authenticator:/oauth2redirect"
        const val RC_AUTH = 9999

        @Volatile
        var pendingResult: CompletableDeferred<Boolean>? = null
    }

    fun startGoogleFlow(activity: Activity): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pendingResult = deferred

        val clientId = prefs.gdriveClientId?.takeIf { it.isNotBlank() }
            ?: CloudKeys.GOOGLE_CLIENT_ID
        if (clientId.startsWith("REPLACE_")) {
            deferred.complete(false)
            return deferred
        }

        val request = AuthorizationRequest.Builder(
            GOOGLE_CONFIG,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        ).apply {
            setScope("https://www.googleapis.com/auth/drive.file")
            setPrompt("consent")
            setAdditionalParameters(mapOf("access_type" to "offline"))
        }.build()

        val intent = authService.getAuthorizationRequestIntent(request)
        activity.startActivityForResult(intent, RC_AUTH)

        return deferred
    }

    fun startDropboxFlow(activity: Activity): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pendingResult = deferred

        val appKey = prefs.dropboxAppKey?.takeIf { it.isNotBlank() }
            ?: CloudKeys.DROPBOX_APP_KEY
        if (appKey.startsWith("REPLACE_")) {
            deferred.complete(false)
            return deferred
        }

        val request = AuthorizationRequest.Builder(
            DROPBOX_CONFIG,
            appKey,
            ResponseTypeValues.CODE,
            Uri.parse(REDIRECT_URI),
        ).apply {
            setAdditionalParameters(mapOf("token_access_type" to "offline"))
        }.build()

        val intent = authService.getAuthorizationRequestIntent(request)
        activity.startActivityForResult(intent, RC_AUTH)

        return deferred
    }

    fun handleResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode != RC_AUTH) return
        val deferred = pendingResult ?: return
        // Clear immediately to prevent double-completion if handleResult is called twice
        pendingResult = null

        if (data == null) {
            deferred.complete(false)
            return
        }

        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)

        if (ex != null || response?.authorizationCode == null) {
            if (BuildConfig.DEBUG) android.util.Log.e("Cernunnos", "OAuth failed: ex=$ex")
            deferred.complete(false)
            return
        }

        // Use AppAuth's token exchange which handles PKCE code_verifier automatically
        val tokenRequest = response.createTokenExchangeRequest()
        tokenExecutor.execute {
            try {
                authService.performTokenRequest(tokenRequest) { tokenResponse, exception ->
                    if (exception != null) {
                        if (BuildConfig.DEBUG) android.util.Log.e("Cernunnos", "Token exchange failed", exception)
                        deferred.complete(false)
                        return@performTokenRequest
                    }
                    if (tokenResponse == null) {
                        if (BuildConfig.DEBUG) android.util.Log.e("Cernunnos", "Token exchange returned null")
                        deferred.complete(false)
                        return@performTokenRequest
                    }

                    // Store tokens
                    val provider = prefs.cloudProvider
                    val accessToken = tokenResponse.accessToken
                    val refreshToken = tokenResponse.refreshToken
                    val expiry = tokenResponse.accessTokenExpirationTime ?: (System.currentTimeMillis() + 3600000)

                    android.util.Log.d("Cernunnos", "Token exchange success: provider=$provider, hasToken=${accessToken != null}, hasRefresh=${refreshToken != null}")

                    when (provider) {
                        "gdrive" -> {
                            prefs.gdriveToken = accessToken
                            if (refreshToken != null) prefs.gdriveRefreshToken = refreshToken
                            prefs.gdriveTokenExpiry = expiry
                        }
                        "dropbox" -> {
                            prefs.dropboxToken = accessToken
                            if (refreshToken != null) prefs.dropboxRefreshToken = refreshToken
                            prefs.dropboxTokenExpiry = expiry
                        }
                    }

                    deferred.complete(true)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.e("Cernunnos", "Token exchange exception", e)
                deferred.complete(false)
            }
        }
    }

    /**
     * Shut down the background executor. Should be called when the manager
     * is no longer needed (e.g. from ViewModel.onCleared()).
     */
    fun shutdown() {
        tokenExecutor.shutdown()
        try {
            tokenExecutor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

}

object ResponseTypeValues {
    const val CODE = "code"
}
