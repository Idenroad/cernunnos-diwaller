package com.cernunnos.authenticator

import android.app.Application
import com.cernunnos.authenticator.crash.CrashReporter

class CernunnosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Install crash reporter — writes crash logs to internal storage.
        // No automatic sending; the user decides whether to share.
        CrashReporter.install(this)
    }

    companion object {
        lateinit var instance: CernunnosApp
            private set
    }
}
