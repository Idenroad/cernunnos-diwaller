package com.cernunnos.authenticator.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cernunnos.authenticator.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for [AppPreferences] using a real device/emulator Context.
 *
 * NOTE: [AppPreferences.secureStorageAvailable] depends on EncryptedSharedPreferences,
 * which requires Android Keystore. On emulators with API 26+ this should be available.
 * If running on a broken/old emulator where Keystore is unavailable, the
 * `secureStorageAvailable_isTrue` test may fail — that indicates an environment issue,
 * not a code defect.
 */
@RunWith(AndroidJUnit4::class)
class AppPreferencesTest {
    private lateinit var prefs: AppPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cernunnos_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("cernunnos_secure_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = AppPreferences(context)
    }

    @Test
    fun autoLockTimeout_defaultIs60() {
        assertEquals(60, prefs.autoLockTimeout)
    }

    @Test
    fun autoLockTimeout_negativeClampedTo0() {
        prefs.autoLockTimeout = -10
        assertEquals(0, prefs.autoLockTimeout)
    }

    @Test
    fun autoLockTimeout_zeroDisablesAutoLock() {
        prefs.autoLockTimeout = 0
        assertEquals(0, prefs.autoLockTimeout)
    }

    @Test
    fun autoLockTimeout_positiveValueStored() {
        prefs.autoLockTimeout = 120
        assertEquals(120, prefs.autoLockTimeout)
    }

    @Test
    fun widgetMaxEntries_defaultIs10() {
        assertEquals(10, prefs.widgetMaxEntries)
    }

    @Test
    fun widgetMaxEntries_below1ClampedTo1() {
        prefs.widgetMaxEntries = 0
        assertEquals(1, prefs.widgetMaxEntries)
    }

    @Test
    fun widgetMaxEntries_above10ClampedTo10() {
        prefs.widgetMaxEntries = 99
        assertEquals(10, prefs.widgetMaxEntries)
    }

    @Test
    fun widgetMaxEntries_inRangeStored() {
        prefs.widgetMaxEntries = 5
        assertEquals(5, prefs.widgetMaxEntries)
    }

    @Test
    fun themeMode_defaultIsDark() {
        assertEquals("dark", prefs.themeMode)
    }

    @Test
    fun themeMode_setToLight() {
        prefs.themeMode = "light"
        assertEquals("light", prefs.themeMode)
    }

    @Test
    fun themeMode_setToSystem() {
        prefs.themeMode = "system"
        assertEquals("system", prefs.themeMode)
    }

    @Test
    fun sortMode_defaultIsName() {
        assertEquals("name", prefs.sortMode)
    }

    @Test
    fun viewMode_defaultIsList() {
        assertEquals("list", prefs.viewMode)
    }

    @Test
    fun language_defaultIsSystem() {
        assertEquals("system", prefs.language)
    }

    @Test
    fun language_setToFrench() {
        prefs.language = "fr"
        assertEquals("fr", prefs.language)
    }

    @Test
    fun secureStorageAvailable_isTrue() {
        // On emulator with API 26+, EncryptedSharedPreferences should work
        assertTrue(prefs.secureStorageAvailable)
    }

    @Test
    fun dropboxToken_storedSecurely() {
        prefs.dropboxToken = "test_token_123"
        assertEquals("test_token_123", prefs.dropboxToken)
    }

    @Test
    fun dropboxToken_nullByDefault() {
        assertNull(prefs.dropboxToken)
    }

    @Test
    fun manualOrder_emptyByDefault() {
        assertTrue(prefs.manualOrder.isEmpty())
    }

    @Test
    fun manualOrder_storedAndRetrieved() {
        prefs.manualOrder = listOf("e3", "e1", "e2")
        assertEquals(listOf("e3", "e1", "e2"), prefs.manualOrder)
    }

    @Test
    fun categories_defaultHas5Categories() {
        val cats = prefs.categories
        assertEquals(5, cats.size)
        assertTrue(cats.any { it.id == "cat_pro" })
        assertTrue(cats.any { it.id == "cat_mail" })
    }

    @Test
    fun categories_customStored() {
        val custom = listOf(
            Category("c1", "Custom1"),
            Category("c2", "Custom2"),
        )
        prefs.categories = custom
        val loaded = prefs.categories
        assertEquals(2, loaded.size)
        assertEquals("c1", loaded[0].id)
        assertEquals("Custom1", loaded[0].name)
    }

    @Test
    fun usageStats_incrementAndRetrieve() {
        prefs.incrementEntryViewCount("e1")
        prefs.incrementEntryViewCount("e1")
        prefs.incrementEntryViewCount("e1")
        assertEquals(3, prefs.getEntryViewCount("e1"))
        assertTrue(prefs.getEntryLastViewed("e1") > 0)
    }

    @Test
    fun usageStats_neverViewed_returns0() {
        assertEquals(0, prefs.getEntryViewCount("never"))
        assertEquals(0L, prefs.getEntryLastViewed("never"))
    }
}
