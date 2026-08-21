package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from andOTP backup.
 *
 * andOTP is an open-source Android authenticator (predecessor of Aegis for some users).
 * Its export format is a JSON array of entry objects:
 * [
 *   {
 *     "secret": "BASE32SECRET",
 *     "issuer": "GitHub",
 *     "label": "user@github.com",
 *     "digits": 6,
 *     "type": "TOTP",
 *     "algorithm": "SHA1",
 *     "thumbnail": null,
 *     "last_used": 0,
 *     "used_frequency": 0,
 *     "period": 30,
 *     "tags": []
 *   }
 * ]
 *
 * Encrypted andOTP exports (.json.aes) are not supported here — the user
 * must export unencrypted from andOTP.
 */
object AndOtpImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class AndOtpEntry(
        val secret: String = "",
        val issuer: String = "",
        val label: String = "",
        val digits: Int = 6,
        val type: String = "TOTP",
        val algorithm: String = "SHA1",
        val period: Int = 30,
        val counter: Long? = null,
        val tags: List<String> = emptyList(),
    )

    /**
     * Parse an andOTP JSON export.
     * @param jsonStr JSON array of andOTP entries.
     * @return List of parsed TotpEntry.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val entries: List<AndOtpEntry> = json.decodeFromString(jsonStr)
        val result = mutableListOf<TotpEntry>()

        for (entry in entries) {
            try {
                if (entry.secret.isBlank()) continue
                val secret = Base32Codec.decode(entry.secret)

                val type = when (entry.type.uppercase()) {
                    "HOTP" -> "hotp"
                    "STEAM" -> "totp" // Steam is TOTP with a different digit format
                    else -> "totp"
                }
                val algorithm = entry.algorithm.uppercase().let {
                    if (it in listOf("SHA1", "SHA256", "SHA512")) it else "SHA1"
                }
                val digits = if (entry.digits in 6..8) entry.digits else 6
                val period = if (entry.period > 0) entry.period else 30

                val id = java.util.UUID.randomUUID().toString()
                result.add(
                    TotpEntry(
                        id = id,
                        issuer = entry.issuer.ifBlank { "andOTP" },
                        label = entry.label,
                        secret = secret,
                        algorithm = algorithm,
                        digits = digits,
                        period = period,
                        type = type,
                        counter = entry.counter ?: 0L,
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }

        return result
    }
}
