package com.cernunnos.authenticator.constants

/**
 * Backup file and rotation constants.
 */
object BackupConfig {
    const val BACKUP_DIR_NAME = "backups"
    const val BACKUP_FILE_PREFIX = "cernunnos_backup_"
    const val BACKUP_FILE_EXTENSION = ".txt"
    const val SINGLE_ENTRY_PREFIX = "cernunnos_totp_"

    const val MAX_BACKUP_COUNT = 10
    const val MIN_BACKUP_FILE_SIZE = 16 // bytes
    const val KEEP_NEWEST_COUNT = 10
}
