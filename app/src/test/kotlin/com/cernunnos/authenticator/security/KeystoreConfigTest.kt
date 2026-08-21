package com.cernunnos.authenticator.security

import com.cernunnos.authenticator.constants.SecurityConfig
import com.cernunnos.authenticator.data.crypto.BiometricVault
import com.cernunnos.authenticator.ui.viewmodel.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * JVM unit tests verifying the Android Keystore / biometric configuration.
 *
 * These tests use reflection because the Android Keystore is not available
 * in JVM unit tests. They verify that the required classes, methods, and
 * constants exist so that any accidental removal or renaming is caught at
 * test time.
 */
class KeystoreConfigTest {

    @Test
    fun biometricVault_classExists() {
        // Verify the class itself is loadable
        val clazz = Class.forName("com.cernunnos.authenticator.data.crypto.BiometricVault")
        assertNotNull("BiometricVault class should exist", clazz)
    }

    @Test
    fun biometricVault_hasPrepareInitializationCipher() {
        val method: Method = BiometricVault::class.java.getDeclaredMethod("prepareInitializationCipher")
        assertNotNull(
            "BiometricVault should have prepareInitializationCipher method",
            method,
        )
    }

    @Test
    fun biometricVault_hasCompleteInitialization() {
        val method: Method = BiometricVault::class.java.getDeclaredMethod(
            "completeInitialization",
            javax.crypto.Cipher::class.java,
        )
        assertNotNull(
            "BiometricVault should have completeInitialization method",
            method,
        )
    }

    @Test
    fun biometricVault_hasDecryptMasterKey() {
        val method: Method = BiometricVault::class.java.getDeclaredMethod(
            "decryptMasterKey",
            javax.crypto.Cipher::class.java,
        )
        assertNotNull(
            "BiometricVault should have decryptMasterKey method",
            method,
        )
    }

    @Test
    fun biometricVault_companionHasAndroidKeystoreConstant() {
        // ANDROID_KEYSTORE is a private const in BiometricVault's companion object.
        // In Kotlin, const val in a companion object is compiled to a static field
        // on the enclosing class.
        val field: Field = BiometricVault::class.java.getDeclaredField("ANDROID_KEYSTORE")
        field.isAccessible = true
        val value = field.get(null) as String
        assertEquals("AndroidKeyStore", value)
    }

    @Test
    fun appViewModel_hasEncryptWithDeviceKey() {
        val method: Method = AppViewModel::class.java.getDeclaredMethod(
            "encryptWithDeviceKey",
            ByteArray::class.java,
        )
        assertNotNull(
            "AppViewModel should have encryptWithDeviceKey method",
            method,
        )
    }

    @Test
    fun appViewModel_hasDecryptWithDeviceKey() {
        val method: Method = AppViewModel::class.java.getDeclaredMethod(
            "decryptWithDeviceKey",
            ByteArray::class.java,
        )
        assertNotNull(
            "AppViewModel should have decryptWithDeviceKey method",
            method,
        )
    }

    @Test
    fun securityConfig_aesKeySize_is256() {
        assertEquals(256, SecurityConfig.AES_KEY_SIZE)
    }

    @Test
    fun securityConfig_gcmTagBits_is128() {
        assertEquals(128, SecurityConfig.GCM_TAG_BITS)
    }
}
