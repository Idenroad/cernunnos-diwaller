package com.cernunnos.authenticator.security

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

object RootDetector {
    fun isRooted(context: Context): Boolean {
        // Check for su binary
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/data/local/su", "/su/bin/su"
        )
        if (paths.any { File(it).exists() }) return true

        // Check for Magisk
        if (File("/sbin/magisk").exists() || File("/system/bin/magisk").exists()) return true

        // Note: busybox check removed — it generates false positives on devices
        // where busybox is legitimately installed (dev tools, terminal emulators).

        // Check for root apps
        val rootApps = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu",
            "com.koushikdutta.superuser", "com.thirdparty.superuser",
            "com.noshufou.android.su", "com.kingouser.com"
        )
        val pm = context.packageManager
        rootApps.forEach { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) { /* not installed — expected */ }
        }

        // Check for Xposed
        try {
            val cl = Class.forName("de.robv.android.xposed.XposedBridge")
            if (cl != null) return true
        } catch (e: ClassNotFoundException) { /* Xposed not installed — expected */ }

        return false
    }

    fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
