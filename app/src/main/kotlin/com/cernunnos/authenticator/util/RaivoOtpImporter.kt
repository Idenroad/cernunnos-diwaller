package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from Raivo OTP export.
 *
 * Ravo OTP is an open-source authenticator (primarily iOS).
 * Its export format is a JSON object with an "entries" array:
 * {
 *   "app": "raivo-otp",
 *   "version": 1,
 *   "entries": [
 *     {
 *       "issuer": "GitHub",
 *       "account": "user@github.com",
 *       "secret": "BASE32SECRET",
 *       "algorithm": "sha1",
 *       "digits": 6,
 *       "period": 30,
 *       "type": "totp",
 *       "counter": 0
 *     }
 *   ]
 * }
 */
object RaivoOtpImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class RaivoExport(
        val app: String? = null,
        val version: Int = 1,
        val entries: List<RaivoEntry> = emptyList(),
    )

    @Serializable
    private data class RaivoEntry(
        val issuer: String = "",
        val account: String = "",
        val secret: String = "",
        val algorithm: String = "sha1",
        val digits: Int = 6,
        val period: Int = 30,
        val type: String = "totp",
        val counter: Long = 0L,
    )

    /**
     * Parse a Raivo OTP JSON export.
     * @param jsonStr JSON content of the Raivo export.
     * @return List of parsed TotpEntry.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val export = json.decodeFromString<RaivoExport>(jsonStr)
        val result = mutableListOf<TotpEntry>()

        for (entry in export.entries) {
            try {
                if (entry.secret.isBlank()) continue
                val secret = Base32Codec.decode(entry.secret)

                val type = when (entry.type.lowercase()) {
                    "hotp" -> "hotp"
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
                        issuer = entry.issuer.ifBlank { "Raivo" },
                        label = entry.account,
                        secret = secret,
                        algorithm = algorithm,
                        digits = digits,
                        period = period,
                        type = type,
                        counter = entry.counter,
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }

        return result
    }
}
