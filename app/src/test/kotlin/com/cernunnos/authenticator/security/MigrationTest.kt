package com.cernunnos.authenticator.security

import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.data.storage.AppPreferences
import com.cernunnos.authenticator.data.storage.DocumentStore
import com.cernunnos.authenticator.data.storage.EncryptedStore
import com.cernunnos.authenticator.util.ExportImport
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito
import java.lang.reflect.Field

/**
 * JVM unit tests verifying migration and versioning infrastructure.
 *
 * These tests assert that:
 * - Preference versioning is in place (CURRENT_PREFS_VERSION >= 2)
 * - The migratePreferences method exists for future migrations
 * - ExportImport uses versioned formats (v0 legacy + v1 current)
 * - Import correctly handles both v0 and v1 formats
 * - Import rejects malformed data
 * - JSON serialization is forward-compatible (ignoreUnknownKeys)
 */
class MigrationTest {

    @Test
    fun currentPrefsVersion_isAtLeast2() {
        // CURRENT_PREFS_VERSION is a private const in the companion object.
        // In Kotlin, const val in a companion object is compiled to a static
        // field on the enclosing class.
        val field: Field = AppPreferences::class.java.getDeclaredField("CURRENT_PREFS_VERSION")
        field.isAccessible = true
        val version = field.get(null) as Int
        assertTrue("CURRENT_PREFS_VERSION should be >= 2, got $version", version >= 2)
    }

    @Test
    fun migratePreferences_methodExists() {
        // Verify that the migration method exists so future version bumps
        // can extend it.
        val method = AppPreferences::class.java.getDeclaredMethod("migratePreferences")
        assertNotNull("AppPreferences should have a migratePreferences method", method)
    }

    @Test
    fun exportImport_currentVersion_isV1() {
        val field: Field = ExportImport::class.java.getDeclaredField("CURRENT_VERSION")
        field.isAccessible = true
        val version = field.get(null) as String
        assertEquals("v1", version)
    }

    @Test
    fun exportImport_importHandlesV0Format() {
        // v0 format: salt:iv:ciphertext (3 parts, no version prefix)
        // The v0 string is derived from the v1 export by stripping the
        // "v1:checksum:" prefix. The underlying encrypted data is identical,
        // so import should successfully decrypt and parse it.
        val entries = ExportImport.import(v0Export, PASSPHRASE)
        assertEquals(1, entries.size)
        assertEquals("test-issuer", entries[0].issuer)
        assertEquals("test@example.com", entries[0].label)
    }

    @Test
    fun exportImport_importHandlesV1Format() {
        // v1 format: v1:checksum:salt:iv:ciphertext (5 parts with checksum)
        val entries = ExportImport.import(v1Export, PASSPHRASE)
        assertEquals(1, entries.size)
        assertEquals("test-issuer", entries[0].issuer)
        assertEquals("test@example.com", entries[0].label)
    }

    @Test
    fun exportImport_importRejectsInvalidFormat() {
        // Wrong number of parts (4 parts is neither v0=3 nor v1=5)
        val invalidData = "part1:part2:part3:part4"
        try {
            ExportImport.import(invalidData, PASSPHRASE)
            fail("Expected import to throw for invalid format (4 parts)")
        } catch (e: Exception) {
            // Expected — the error message mentions the part count
            val msg = e.message ?: ""
            assertTrue(
                "Error should mention invalid format, got: $msg",
                msg.contains("Invalid export format") || msg.contains("parts")
            )
        }
    }

    @Test
    fun encryptedStore_json_hasIgnoreUnknownKeys() {
        // Verify that EncryptedStore's Json instance is configured with
        // ignoreUnknownKeys = true for forward-compatible deserialization.
        val store = createEncryptedStore()
        val jsonField = EncryptedStore::class.java.getDeclaredField("json")
        jsonField.isAccessible = true
        val json = jsonField.get(store) as Json
        assertTrue(
            "EncryptedStore should use Json with ignoreUnknownKeys=true",
            json.configuration.ignoreUnknownKeys
        )
    }

    @Test
    fun documentStore_json_hasIgnoreUnknownKeys() {
        // Verify that DocumentStore's Json instance is configured with
        // ignoreUnknownKeys = true for forward-compatible deserialization.
        val store = createDocumentStore()
        val jsonField = DocumentStore::class.java.getDeclaredField("json")
        jsonField.isAccessible = true
        val json = jsonField.get(store) as Json
        assertTrue(
            "DocumentStore should use Json with ignoreUnknownKeys=true",
            json.configuration.ignoreUnknownKeys
        )
    }

    // ── Helpers ──

    /**
     * Create an EncryptedStore instance using a mock Context.
     *
     * Mockito creates a subclass of Context at runtime. With the Android
     * Gradle plugin's `isReturnDefaultValues = true` option, the stub
     * Android SDK methods return default values (null for objects), so
     * the mock Context's methods also return null. This is sufficient
     * for the field initializers in EncryptedStore that don't depend
     * on Context return values (specifically the `json` field).
     */
    private fun createEncryptedStore(): EncryptedStore {
        val context = Mockito.mock(android.content.Context::class.java)
        return EncryptedStore(context)
    }

    private fun createDocumentStore(): DocumentStore {
        val context = Mockito.mock(android.content.Context::class.java)
        return DocumentStore(context)
    }

    companion object {
        private const val PASSPHRASE = "testpass123"
        private lateinit var v1Export: String
        private lateinit var v0Export: String

        @BeforeClass
        @JvmStatic
        fun createExportData() {
            // Create a single test entry and export it in v1 format.
            // The v1 export uses CryptoManager.encrypt which derives a key
            // via Argon2id (Bouncy Castle, pure Java — works in JVM tests).
            // We do this once in @BeforeClass to avoid repeated Argon2id
            // derivations across tests.
            val entries = listOf(
                TotpEntry(
                    id = "test-id-1",
                    issuer = "test-issuer",
                    label = "test@example.com",
                    secret = "JBSWY3DPEHPK3PXP".toByteArray(),
                    algorithm = "SHA1",
                    digits = 6,
                    period = 30,
                )
            )
            v1Export = ExportImport.export(entries, PASSPHRASE)

            // Derive v0 format by stripping the "v1:checksum:" prefix.
            // v1 format: v1:checksum:salt:iv:ciphertext
            // v0 format: salt:iv:ciphertext
            val parts = v1Export.split(":")
            v0Export = parts.subList(2, parts.size).joinToString(":")
        }
    }
}
