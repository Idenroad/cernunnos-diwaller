package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from Microsoft Authenticator backup.
 *
 * Microsoft Authenticator stores entries in a JSON format when exported.
 * The format is typically a JSON object with a "records" or "accounts" array.
 * Each record contains:
 * - "accountName" or "name": the account label
 * - "issuer" or "accountName": the issuer
 * - "secretKey": base32-encoded secret
 * - "oathType": "totp" or "hotp"
 * - "algorithm": "SHA1", "SHA256", etc.
 * - "digits": number of digits
 * - "period": period in seconds
 *
 * Note: Microsoft Authenticator does not provide an official export feature.
 * This importer handles the JSON format produced by third-party tools
 * and the internal backup format.
 */
object MicrosoftAuthImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class MicrosoftEntry(
        val accountName: String? = null,
        val name: String? = null,
        val issuer: String? = null,
        val secretKey: String? = null,
        val oathType: String? = null,
        val algorithm: String? = null,
        val digits: Int? = null,
        val period: Int? = null,
        val counter: Long? = null,
        val timeStep: Int? = null,
    )

    @Serializable
    private data class MicrosoftExport(
        val records: List<MicrosoftEntry> = emptyList(),
        val accounts: List<MicrosoftEntry> = emptyList(),
        val tokens: List<MicrosoftEntry> = emptyList(),
    )

    /**
     * Parse a Microsoft Authenticator JSON export.
     * @param jsonStr JSON content of the export.
     * @return List of parsed TotpEntry.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val trimmed = jsonStr.trim()
        val entries: List<MicrosoftEntry> = if (trimmed.startsWith("[")) {
            json.decodeFromString(trimmed)
        } else if (trimmed.startsWith("{")) {
            val export = json.decodeFromString<MicrosoftExport>(trimmed)
            export.records.ifEmpty { export.accounts.ifEmpty { export.tokens } }
        } else {
            return emptyList()
        }

        val result = mutableListOf<TotpEntry>()
        for (entry in entries) {
            try {
                val secret = entry.secretKey ?: continue
                if (secret.isBlank()) continue
                val secretBytes = Base32Codec.decode(secret)

                val type = when (entry.oathType?.lowercase()) {
                    "hotp" -> "hotp"
                    else -> "totp"
                }
                val algorithm = entry.algorithm?.uppercase()?.let {
                    if (it in listOf("SHA1", "SHA256", "SHA512")) it else "SHA1"
                } ?: "SHA1"
                val digits = entry.digits?.let { if (it in 6..8) it else 6 } ?: 6
                val period = entry.period ?: entry.timeStep ?: 30
                val periodSafe = if (period > 0) period else 30

                val issuer = entry.issuer ?: entry.accountName ?: ""
                val label = entry.accountName ?: entry.name ?: ""

                val id = java.util.UUID.randomUUID().toString()
                result.add(
                    TotpEntry(
                        id = id,
                        issuer = issuer.ifBlank { "Microsoft" },
                        label = label,
                        secret = secretBytes,
                        algorithm = algorithm,
                        digits = digits,
                        period = periodSafe,
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
