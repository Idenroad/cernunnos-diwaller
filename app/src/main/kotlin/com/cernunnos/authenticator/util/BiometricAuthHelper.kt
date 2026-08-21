package com.cernunnos.authenticator.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Helper to check if device credential authentication is available
 * and to launch BiometricPrompt.
 *
 * The cipher MUST be passed as a CryptoObject so that the Keystore binds
 * the authentication to the crypto operation. Without CryptoObject, the
 * Keystore rejects the cipher.doFinal() call with "Key user not authenticated"
 * because setUserAuthenticationRequired(true) is set on the key.
 */
object BiometricAuthHelper {

    fun isDeviceCredentialAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Launch BiometricPrompt for authentication with a CryptoObject.
     * The cipher is bound to the authentication — after onSuccess(), the cipher
     * can be used for encryption/decryption without further auth.
     *
     * @param cipher The cipher to authenticate (must be initialized before passing)
     * @param onSuccess Called with the authenticated cipher on success
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // The cipher is now authenticated — retrieve it from the result
                val authCipher = result.cryptoObject?.cipher ?: cipher
                onSuccess(authCipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                // User will retry
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG
            )
            .build()

        // Pass the cipher as a CryptoObject so the Keystore binds auth to the crypto op
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
