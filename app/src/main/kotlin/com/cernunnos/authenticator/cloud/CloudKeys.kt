package com.cernunnos.authenticator.cloud

import com.cernunnos.authenticator.BuildConfig

/**
 * Cloud provider API keys.
 *
 * Keys are injected at build time from a .env file (see .env.example).
 * They are accessed via BuildConfig fields, NOT hardcoded in source.
 *
 * To configure your own keys:
 *   1. Copy .env.example to .env in the project root
 *   2. Fill in your Google Client ID and Dropbox App Key
 *   3. Rebuild the app
 *
 * If no .env file is present, placeholder values are used and cloud
 * features will be disabled (the app checks for "REPLACE_" prefix).
 */
object CloudKeys {
    // Google Drive OAuth — injected from .env via BuildConfig
    val GOOGLE_CLIENT_ID: String = BuildConfig.GOOGLE_CLIENT_ID

    // Dropbox OAuth — injected from .env via BuildConfig
    val DROPBOX_APP_KEY: String = BuildConfig.DROPBOX_APP_KEY
}
