package com.cernunnos.authenticator.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit tests that audit the merged AndroidManifest.xml for security-relevant
 * attributes.
 *
 * Because this is a plain JVM test (no Android framework), the manifest is read
 * directly from the file system as text and parsed with simple string checks.
 */
class ManifestSecurityTest {

    private val manifest: String =
        File("src/main/AndroidManifest.xml").readText()

    @Test
    fun manifest_disablesAllowBackup() {
        assertTrue(
            "android:allowBackup must be \"false\"",
            manifest.contains("android:allowBackup=\"false\""),
        )
    }

    @Test
    fun manifest_disablesFullBackupContent() {
        assertTrue(
            "android:fullBackupContent must be \"false\"",
            manifest.contains("android:fullBackupContent=\"false\""),
        )
    }

    @Test
    fun manifest_doesNotEnableCleartextTraffic() {
        assertFalse(
            "android:usesCleartextTraffic must NOT be \"true\"",
            manifest.contains("android:usesCleartextTraffic=\"true\""),
        )
    }

    @Test
    fun manifest_declaresNetworkSecurityConfig() {
        assertTrue(
            "android:networkSecurityConfig must be declared",
            manifest.contains("android:networkSecurityConfig"),
        )
    }

    @Test
    fun manifest_supportsRtl() {
        assertTrue(
            "android:supportsRtl must be \"true\"",
            manifest.contains("android:supportsRtl=\"true\""),
        )
    }

    @Test
    fun manifest_doesNotUseWorldReadable() {
        assertFalse(
            "MODE_WORLD_READABLE must NOT appear in the manifest",
            manifest.contains("MODE_WORLD_READABLE"),
        )
    }

    @Test
    fun manifest_exportedComponentsAreLimited() {
        val exportedCount = Regex("android:exported=\"true\"").findAll(manifest).count()
        assertTrue(
            "There should be fewer than 10 exported components (found $exportedCount)",
            exportedCount < 10,
        )
    }
}
