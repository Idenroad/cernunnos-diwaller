package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from Steam Guard (Steam Authenticator) export.
 *
 * Steam Guard uses TOTP with a custom algorithm but the secret can be
 * imported as a standard TOTP entry. The Steam app generates 5-character
 * alphanumeric codes, but the underlying secret is a base64-encoded key.
 *
 * The export format from tools like Steam Desktop Authenticator is a JSON
 * object with:
 * - "shared_secret": base64-encoded secret
 * - "account_name": account name
 * - "device_id": device ID (ignored)
 *
 * Note: Steam codes use a custom algorithm (not standard TOTP) and will
 * not generate correct Steam codes in Cernunnos. The secret is imported
 * as a standard TOTP entry for backup purposes. Users should keep their
 * Steam Guard on a separate device.
 */
object SteamImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class SteamExport(
        val shared_secret: String = "",
        val account_name: String = "",
        val device_id: String? = null,
        val Session: SteamSession? = null,
    )

    @Serializable
    private data class SteamSession(
        val SessionID: String? = null,
        val SteamID: String? = null,
        val AccessToken: String? = null,
    )

    /**
     * Parse a Steam Guard JSON export.
     * @param jsonStr JSON content of the Steam export.
     * @return List containing one TotpEntry (or empty if invalid).
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val export = json.decodeFromString<SteamExport>(jsonStr)
        if (export.shared_secret.isBlank()) return emptyList()

        return try {
            // Steam secrets are base64-encoded (not base32)
            val secret = java.util.Base64.getDecoder().decode(export.shared_secret)
            val id = java.util.UUID.randomUUID().toString()
            listOf(
                TotpEntry(
                    id = id,
                    issuer = "Steam",
                    label = export.account_name.ifBlank { "Steam Guard" },
                    secret = secret,
                    algorithm = "SHA1",
                    digits = 5, // Steam uses 5-character codes
                    period = 30,
                    type = "totp",
                )
            )
        } catch (e: Exception) {
            emptyList()
        }
    }
}
