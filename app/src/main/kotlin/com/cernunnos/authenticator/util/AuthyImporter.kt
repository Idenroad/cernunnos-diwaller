package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Import TOTP entries from Authy backup/export.
 *
 * Authy stores entries in a JSON format when exported via backup tools.
 * The format is an array of objects with:
 * - "name": account name
 * - "issuer": issuer name (sometimes in name)
 * - "secret": base32-encoded secret
 * - "digits": number of digits (usually 6 or 7)
 * - "algorithm": "SHA1", "SHA256", etc.
 *
 * Note: Authy does not provide an official export feature. This importer
 * handles the JSON format produced by third-party Authy export tools
 * (e.g. https://github.com/alexzorin/authy) which output a JSON array.
 */
object AuthyImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class AuthyEntry(
        val name: String = "",
        val issuer: String? = null,
        val secret: String = "",
        val digits: Int = 6,
        val algorithm: String = "SHA1",
        val period: Int = 30,
        val accountType: String? = null,
    )

    /**
     * Parse an Authy JSON export (array of entry objects).
     * @param jsonStr JSON array of Authy entries.
     * @return List of parsed TotpEntry.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val trimmed = jsonStr.trim()
        // Handle both array and object-with-array formats
        val entries: List<AuthyEntry> = if (trimmed.startsWith("[")) {
            json.decodeFromString(trimmed)
        } else if (trimmed.startsWith("{")) {
            // Some exporters wrap in an object with a "tokens" or "entries" key
            val obj = json.parseToJsonElement(trimmed).let {
                kotlinx.serialization.json.JsonObject.serializer().let { _ -> it }
            }
            val arrayElement = obj.jsonObject["tokens"]
                ?: obj.jsonObject["entries"]
                ?: obj.jsonObject["authenticators"]
                ?: return emptyList()
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AuthyEntry.serializer()), arrayElement.toString())
        } else {
            return emptyList()
        }

        val result = mutableListOf<TotpEntry>()
        for (entry in entries) {
            try {
                if (entry.secret.isBlank()) continue
                val secret = Base32Codec.decode(entry.secret)
                val algorithm = entry.algorithm.uppercase().let {
                    if (it in listOf("SHA1", "SHA256", "SHA512")) it else "SHA1"
                }
                val digits = if (entry.digits in 6..8) entry.digits else 6
                val period = if (entry.period > 0) entry.period else 30

                // Parse issuer from name if issuer is not provided
                // Authy often puts "Issuer (account)" in the name field
                val (parsedIssuer, parsedLabel) = if (entry.issuer.isNullOrBlank()) {
                    parseAuthyName(entry.name)
                } else {
                    entry.issuer to entry.name
                }

                val id = java.util.UUID.randomUUID().toString()
                result.add(
                    TotpEntry(
                        id = id,
                        issuer = parsedIssuer.ifBlank { "Authy" },
                        label = parsedLabel,
                        secret = secret,
                        algorithm = algorithm,
                        digits = digits,
                        period = period,
                        type = "totp",
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }

        return result
    }

    /**
     * Parse Authy's "Issuer (Account)" or "Issuer: Account" format.
     */
    private fun parseAuthyName(name: String): Pair<String, String> {
        // "Amazon (user@email.com)" → issuer=Amazon, label=user@email.com
        val parenIdx = name.lastIndexOf('(')
        if (parenIdx > 0 && name.endsWith(")")) {
            val issuer = name.substring(0, parenIdx).trim()
            val label = name.substring(parenIdx + 1, name.length - 1).trim()
            return issuer to label
        }
        // "Issuer: account" format
        val colonIdx = name.indexOf(':')
        if (colonIdx > 0) {
            val issuer = name.substring(0, colonIdx).trim()
            val label = name.substring(colonIdx + 1).trim()
            return issuer to label
        }
        return "" to name
    }
}

// Extension to access jsonObject without importing at top level
private val kotlinx.serialization.json.JsonElement.jsonObject
    get() = this as kotlinx.serialization.json.JsonObject
