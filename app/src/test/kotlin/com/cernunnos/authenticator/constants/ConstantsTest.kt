package com.cernunnos.authenticator.constants

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests verifying the consistency of the project configuration constants.
 */
class ConstantsTest {

    // --- TotpConfig ---

    @Test
    fun TotpConfig_DEFAULT_DIGITS_is6() {
        assertEquals(6, TotpConfig.DEFAULT_DIGITS)
    }

    @Test
    fun TotpConfig_DEFAULT_PERIOD_is30() {
        assertEquals(30, TotpConfig.DEFAULT_PERIOD)
    }

    @Test
    fun TotpConfig_DEFAULT_ALGORITHM_isSHA1() {
        assertEquals(TotpConfig.ALGO_SHA1, TotpConfig.DEFAULT_ALGORITHM)
    }

    @Test
    fun TotpConfig_SUPPORTED_ALGORITHMS_containsSHA1_SHA256_SHA512() {
        val algos = TotpConfig.SUPPORTED_ALGORITHMS
        assertTrue(TotpConfig.ALGO_SHA1 in algos)
        assertTrue(TotpConfig.ALGO_SHA256 in algos)
        assertTrue(TotpConfig.ALGO_SHA512 in algos)
        assertEquals(3, algos.size)
    }

    // --- SecurityConfig ---

    @Test
    fun SecurityConfig_ARGON2_SALT_SIZE_is16() {
        assertEquals(16, SecurityConfig.ARGON2_SALT_SIZE)
    }

    @Test
    fun SecurityConfig_AES_KEY_SIZE_is256() {
        assertEquals(256, SecurityConfig.AES_KEY_SIZE)
    }

    @Test
    fun SecurityConfig_GCM_TAG_SIZE_is16() {
        // GCM_TAG_BITS = 128 -> 16 bytes
        assertEquals(128, SecurityConfig.GCM_TAG_BITS)
        assertEquals(16, SecurityConfig.GCM_TAG_BITS / 8)
    }

    @Test
    fun SecurityConfig_PASSPHRASE_MIN_LENGTH_is8() {
        assertEquals(8, SecurityConfig.MIN_PASSPHRASE_LENGTH)
    }

    // --- CloudConfig ---

    @Test
    fun CloudConfig_DEFAULT_TIMEOUT_isPositive() {
        assertTrue(CloudConfig.TIMEOUT_SHORT > 0)
        assertTrue(CloudConfig.TIMEOUT_STANDARD > 0)
        assertTrue(CloudConfig.TIMEOUT_UPLOAD > 0)
        assertTrue(CloudConfig.TIMEOUT_DOWNLOAD > 0)
    }

    @Test
    fun CloudConfig_TEMP_EXTENSION_isTmp() {
        assertEquals(".tmp", CloudConfig.TEMP_FILE_EXTENSION)
    }

    // --- BackupConfig ---

    @Test
    fun BackupConfig_BACKUP_PREFIX_isNotEmpty() {
        assertTrue(BackupConfig.BACKUP_FILE_PREFIX.isNotEmpty())
    }

    @Test
    fun BackupConfig_MAX_BACKUPS_isPositive() {
        assertTrue(BackupConfig.MAX_BACKUP_COUNT > 0)
        assertTrue(BackupConfig.KEEP_NEWEST_COUNT > 0)
    }
}
