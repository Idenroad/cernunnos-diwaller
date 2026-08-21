package com.cernunnos.authenticator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.cernunnos.authenticator.crash.CrashReportDialog
import com.cernunnos.authenticator.crash.CrashReporter
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.security.RootDetector
import com.cernunnos.authenticator.ui.nav.CernunnosNavHost
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel

class MainActivity : FragmentActivity() {

    private lateinit var appViewModel: AppViewModel
    private lateinit var prefs: AppPreferences

    companion object {
        // URI of a .cern file to decrypt, set when the app is launched via intent.
        // Persisted via savedStateHandle to survive process death.
        private const val STATE_PENDING_CERN_URI = "pending_cern_uri"
        private const val STATE_CERN_TRIGGER = "cern_trigger"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        // Block screenshots and screen recording unless the user explicitly allowed it
        if (!prefs.allowScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()
        // Apply saved language preference at startup
        applyLanguage(prefs.language)
        appViewModel = ViewModelProvider(this)[AppViewModel::class.java]
        // Restore pending .cern URI from saved instance state if process was killed
        savedInstanceState?.let {
            val savedUri = it.getString(STATE_PENDING_CERN_URI)
            if (savedUri != null) {
                appViewModel.setPendingCernUri(savedUri)
            }
        }
        setContent {
            var showCrashDialog by remember { mutableStateOf(false) }
            // Check for pending crash logs on launch
            LaunchedEffect(Unit) {
                if (CrashReporter.hasPendingCrashes(this@MainActivity)) {
                    showCrashDialog = true
                }
            }
            CernunnosNavHost()
            if (showCrashDialog) {
                CrashReportDialog(onDismiss = { showCrashDialog = false })
            }
        }
        // Handle otpauth:// deep link if present in the launch intent
        handleOtpAuthIntent(intent)
        // Handle .cern file open intent
        handleCernFileIntent(intent)
        // Warn the user if the device appears to be rooted
        checkRootWarning()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Persist pending .cern URI so it survives process death
        appViewModel.getPendingCernUri()?.let { outState.putString(STATE_PENDING_CERN_URI, it) }
    }

    /**
     * Show a non-blocking warning dialog at startup if the device appears rooted.
     * The user may choose to continue or exit the app.
     */
    private fun checkRootWarning() {
        if (RootDetector.isRooted(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.security_root_warning_title)
                .setMessage(R.string.security_root_warning_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.security_root_continue) { dialog, _ ->
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.security_root_exit) { _, _ ->
                    finish()
                }
                .show()
        }
    }

    /**
     * Apply the language preference using AppCompatDelegate (AndroidX AppCompat 1.6+).
     * "system" (or empty) → follow system default; "en" → English; "fr" → Français.
     */
    private fun applyLanguage(language: String) {
        val locales = when (language) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "fr" -> LocaleListCompat.forLanguageTags("fr")
            else -> LocaleListCompat.getEmptyLocaleList() // system default
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * Extract an otpauth:// URI from an intent (VIEW action with otpauth scheme)
     * and forward it to the ViewModel for processing.
     */
    private fun handleOtpAuthIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        if (uri.scheme != "otpauth") return
        val uriString = uri.toString()
        if (uriString.length > 2048) return  // Reject excessively long URIs
        appViewModel.handleOtpAuthUri(uriString)
        // Clear the intent data so it's not re-processed on configuration change
        intent.data = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOtpAuthIntent(intent)
        handleCernFileIntent(intent)
    }

    /**
     * Detect a .cern file open intent and store the URI for the NavGraph to navigate.
     * The URI is stored in the ViewModel (which survives configuration changes)
     * and persisted via onSaveInstanceState for process death recovery.
     */
    private fun handleCernFileIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        // Check if it's a .cern file (by extension or mime type)
        val path = uri.lastPathSegment ?: ""
        val mime = intent.type ?: ""
        if (path.endsWith(".cern", ignoreCase = true) || mime == "application/x-cernunnos") {
            appViewModel.setPendingCernUri(uri.toString())
            // Clear intent data to avoid re-processing
            intent.data = null
        }
    }

    /**
     * Called by the system on every user interaction (touch, keypress, scroll, etc).
     * We forward it to the ViewModel to reset the auto-lock timer.
     */
    override fun onUserInteraction() {
        super.onUserInteraction()
        appViewModel.onUserActivity()
    }

    override fun onPause() {
        super.onPause()
        // Save timestamp so we can auto-lock on resume if too much time elapsed
        prefs.lastPauseTime = System.currentTimeMillis()
        // Stop ticker + auto-lock checker, flush pending backup
        appViewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        // Restart ticker + auto-lock checker
        appViewModel.onAppForegrounded()
        // Check if vault should be re-locked after long background inactivity
        val pauseTime = prefs.lastPauseTime
        if (pauseTime > 0) {
            val elapsedSec = (System.currentTimeMillis() - pauseTime) / 1000
            val timeout = prefs.autoLockTimeout
            if (timeout > 0 && elapsedSec >= timeout) {
                appViewModel.lock()
            }
            prefs.lastPauseTime = 0
        }
    }

    /**
     * Handle OAuth redirect result from AppAuth.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        appViewModel.handleOAuthResult(requestCode, resultCode, data)
    }
}
