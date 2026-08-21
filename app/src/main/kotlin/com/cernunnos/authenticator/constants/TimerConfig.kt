package com.cernunnos.authenticator.constants

/**
 * Timer intervals and durations.
 */
object TimerConfig {
    const val TICK_INTERVAL_MS = 1000L       // 1s — TOTP tick
    const val AUTO_LOCK_CHECK_MS = 5000L     // 5s — auto-lock check
    const val SPLASH_ANIMATION_MS = 5000L    // 5s — splash animation
    const val SPLASH_TIMEOUT_MS = 15000L     // 15s — splash timeout
}
