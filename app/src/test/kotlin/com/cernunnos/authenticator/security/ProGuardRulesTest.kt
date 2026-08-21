package com.cernunnos.authenticator.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit tests that audit the ProGuard/R8 rules and the release build type
 * to ensure security-relevant libraries are kept and logging is stripped in
 * release builds.
 */
class ProGuardRulesTest {

    private val proguard: String =
        File("proguard-rules.pro").readText()

    private val buildGradle: String =
        File("build.gradle.kts").readText()

    @Test
    fun proguard_keepsBouncyCastle() {
        assertTrue(
            "proguard-rules.pro must keep Bouncy Castle (org.bouncycastle)",
            proguard.contains("org.bouncycastle"),
        )
    }

    @Test
    fun proguard_keepsZXing() {
        assertTrue(
            "proguard-rules.pro must keep ZXing (com.google.zxing)",
            proguard.contains("com.google.zxing"),
        )
    }

    @Test
    fun proguard_keepsKotlinSerialization() {
        assertTrue(
            "proguard-rules.pro must keep Kotlin serialization (kotlinx.serialization)",
            proguard.contains("kotlinx.serialization"),
        )
    }

    @Test
    fun proguard_keepsAppAuth() {
        assertTrue(
            "proguard-rules.pro must keep AppAuth (net.openid.appauth)",
            proguard.contains("net.openid.appauth"),
        )
    }

    @Test
    fun proguard_keepsJSch() {
        assertTrue(
            "proguard-rules.pro must keep JSch (com.jcraft.jsch)",
            proguard.contains("com.jcraft.jsch"),
        )
    }

    @Test
    fun proguard_keepsEncryptedSharedPreferences() {
        assertTrue(
            "proguard-rules.pro must keep EncryptedSharedPreferences (androidx.security.crypto)",
            proguard.contains("androidx.security.crypto"),
        )
    }

    @Test
    fun proguard_removesLoggingInRelease() {
        assertTrue(
            "proguard-rules.pro must contain -assumenosideeffects for android.util.Log",
            proguard.contains("-assumenosideeffects class android.util.Log"),
        )
    }

    @Test
    fun buildGradle_releaseMinifyEnabled() {
        // Locate the release build type block and assert isMinifyEnabled = true.
        val releaseBlock = buildGradle.substringAfter("release {").substringBefore("debug {")
        assertTrue(
            "release build type must have isMinifyEnabled = true",
            releaseBlock.contains("isMinifyEnabled = true"),
        )
        assertFalse(
            "release build type must NOT have isMinifyEnabled = false",
            releaseBlock.contains("isMinifyEnabled = false"),
        )
    }
}
