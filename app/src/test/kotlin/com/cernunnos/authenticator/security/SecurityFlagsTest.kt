package com.cernunnos.authenticator.security

import com.cernunnos.authenticator.constants.SecurityConfig
import com.cernunnos.authenticator.util.ExportImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field

/**
 * JVM unit tests verifying security-related configuration constants and flags.
 *
 * These tests assert the compile-time values of [SecurityConfig] and
 * [ExportImport] via reflection so that any accidental weakening of the
 * cryptography settings (e.g. reducing the GCM tag length, shrinking the salt,
 * or downgrading the cipher) is caught at test time.
 */
class SecurityFlagsTest {

    @Test
    fun cipherAlgorithm_isAesGcmNoPadding() {
        assertEquals("AES/GCM/NoPadding", SecurityConfig.CIPHER_AES_GCM)
    }

    @Test
    fun gcmTagBits_is128() {
        assertEquals(128, SecurityConfig.GCM_TAG_BITS)
    }

    @Test
    fun argon2SaltSize_isAtLeast16Bytes() {
        assertTrue(SecurityConfig.ARGON2_SALT_SIZE >= 16)
    }

    @Test
    fun aesKeySize_is256() {
        assertEquals(256, SecurityConfig.AES_KEY_SIZE)
    }

    @Test
    fun exportImport_currentVersion_isNotEmpty() {
        // CURRENT_VERSION is a private constant; read it via reflection so the
        // test does not depend on a public accessor.
        val field: Field = ExportImport::class.java.getDeclaredField("CURRENT_VERSION")
        field.isAccessible = true
        val version = field.get(null) as String
        assertFalse(version.isEmpty())
    }
}
