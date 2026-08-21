package com.cernunnos.authenticator.util

import android.content.Context
import android.provider.Settings

/**
 * Detects if accessibility services are enabled.
 * Warns the user if unknown services are active, as they could read the screen.
 */
object AccessibilityDetector {

    data class AccessibilityState(
        val enabled: Boolean,
        val services: List<String>,
    )

    fun getState(context: Context): AccessibilityState {
        val enabled = try {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (enabled != 1) return AccessibilityState(false, emptyList())

        val servicesStr = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return AccessibilityState(false, emptyList())

        val services = if (servicesStr.isEmpty()) emptyList()
        else servicesStr.split(":")

        return AccessibilityState(true, services.toList())
    }

    fun isAccessibilityEnabled(context: Context): Boolean = getState(context).enabled
}
