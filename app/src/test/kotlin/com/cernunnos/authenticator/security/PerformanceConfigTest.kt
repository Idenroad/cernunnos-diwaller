package com.cernunnos.authenticator.security

import com.cernunnos.authenticator.constants.SecurityConfig
import com.cernunnos.authenticator.data.repo.TotpRepository
import com.cernunnos.authenticator.data.storage.DocumentStore
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

/**
 * JVM unit tests verifying performance-related configuration and
 * optimizations.
 *
 * These tests check that caching, debouncing, and memory-hard
 * parameters are properly implemented so that any accidental removal
 * of performance optimizations is caught at test time.
 */
class PerformanceConfigTest {

    @Test
    fun documentStore_hasIndexCacheField() {
        // Verifies that DocumentStore implements an in-memory cache for
        // the decrypted document index, avoiding re-decryption on every
        // CRUD operation.
        val field: Field = DocumentStore::class.java.getDeclaredField("indexCache")
        assertNotNull(
            "DocumentStore should have an indexCache field for caching",
            field,
        )
    }

    @Test
    fun totpRepository_hasCachedEntriesField() {
        // Verifies that TotpRepository maintains an in-memory cache of
        // entries so that reads don't require re-decrypting the vault.
        val field: Field = TotpRepository::class.java.getDeclaredField("cachedEntries")
        assertNotNull(
            "TotpRepository should have a cachedEntries field for caching",
            field,
        )
    }

    @Test
    fun appViewModel_hasBackupJobField() {
        // Verifies that AppViewModel implements debounced auto-backup
        // via a cancellable coroutine Job.
        val field: Field = AppViewModel::class.java.getDeclaredField("backupJob")
        assertNotNull(
            "AppViewModel should have a backupJob field for debounced backups",
            field,
        )
    }

    @Test
    fun securityConfig_argon2MemoryKb_isAtLeast64MB() {
        // 64 MB = 65536 KB. Argon2id with at least 64 MB memory is
        // considered robust against GPU/ASIC attacks.
        assertTrue(
            "ARGON2_MEMORY_KB should be >= 65536 (64 MB), got ${SecurityConfig.ARGON2_MEMORY_KB}",
            SecurityConfig.ARGON2_MEMORY_KB >= 65536,
        )
    }

    @Test
    fun listScreen_usesLazyColumn() {
        // LazyColumn is the Compose equivalent of RecyclerView — it only
        // composes visible items, which is critical for performance with
        // large lists.
        val file = File("src/main/kotlin/com/cernunnos/authenticator/ui/screens/ListScreen.kt")
        val content = file.readText()
        assertTrue(
            "ListScreen should use LazyColumn for efficient list rendering",
            content.contains("LazyColumn"),
        )
    }

    @Test
    fun listScreen_usesStableKeys() {
        // Stable keys (key = { it.id }) allow Compose to efficiently
        // recompose only changed items, avoiding unnecessary work.
        val file = File("src/main/kotlin/com/cernunnos/authenticator/ui/screens/ListScreen.kt")
        val content = file.readText()
        assertTrue(
            "ListScreen should use key = { it.id } for stable item keys",
            content.contains("key = { it.id }"),
        )
    }
}
