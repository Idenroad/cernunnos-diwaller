package com.cernunnos.authenticator.constants

/**
 * SharedPreferences names and vault keys.
 */
object StorageConfig {
    // SharedPreferences names
    const val PREFS_NAME = "cernunnos_prefs"
    const val SECURE_PREFS_NAME = "cernunnos_secure_prefs"
    const val VAULT_PREFS_NAME = "cernunnos_vault"

    // Vault keys
    const val KEY_VAULT_SALT = "vault_salt"
    const val KEY_VAULT_IV = "vault_iv"
    const val KEY_VAULT_DATA = "vault_data"
    const val KEY_VAULT_MODE = "vault_mode"
    const val KEY_VAULT_SETUP = "vault_setup"
    const val KEY_VAULT_BIO_IV = "vault_bio_iv"
    const val KEY_VAULT_BIO_DATA = "vault_bio_data"
    const val KEY_VAULT_IV_BACKUP = "vault_iv_backup"
    const val KEY_VAULT_DATA_BACKUP = "vault_data_backup"
    const val KEY_VAULT_SALT_BACKUP = "vault_salt_backup"
}
