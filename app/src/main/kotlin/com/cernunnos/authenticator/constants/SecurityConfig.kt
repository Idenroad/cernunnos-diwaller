package com.cernunnos.authenticator.constants

/**
 * Security and cryptography constants.
 */
object SecurityConfig {
    // Passphrase
    const val MIN_PASSPHRASE_LENGTH = 8

    // AES-GCM
    const val GCM_TAG_BITS = 128
    const val IV_SIZE = 12 // 96-bit IV for GCM
    const val AES_KEY_SIZE = 256
    const val CIPHER_AES_GCM = "AES/GCM/NoPadding"

    // Argon2id
    const val ARGON2_ITERATIONS = 4
    const val ARGON2_MEMORY_KB = 98304 // 96 MB
    const val ARGON2_PARALLELISM = 4
    const val ARGON2_OUTPUT_LENGTH = 32 // AES-256
    const val ARGON2_SALT_SIZE = 16

    // Android Keystore
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEYSTORE_KEY_ALIAS = "cernunnos_vault_key"
}
