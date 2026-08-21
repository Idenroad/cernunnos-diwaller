package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from Bitwarden Authenticator JSON or CSV export.
 *
 * JSON format:
 * {
 *   "encrypted": false,
 *   "items": [
 *     {
 *       "id": "uuid",
 *       "name": "Amazon",
 *       "type": 1,
 *       "login": {
 *         "totp": "otpauth://totp/Amazon:user@email.com?secret=BASE32&issuer=Amazon&...",
 *         "username": "user@email.com"
 *       },
 *       "favorite": false
 *     }
 *   ]
 * }
 *
 * CSV format (header):
 * folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
 */
object BitwardenImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class BitwardenExport(
        val encrypted: Boolean = false,
        val items: List<BitwardenItem> = emptyList(),
    )

    @Serializable
    private data class BitwardenItem(
        val id: String? = null,
        val name: String? = null,
        val type: Int = 1,
        val login: BitwardenLogin? = null,
        val favorite: Boolean = false,
    )

    @Serializable
    private data class BitwardenLogin(
        val totp: String? = null,
        val username: String? = null,
    )

    /**
     * Parse a Bitwarden Authenticator JSON export.
     * Returns list of TotpEntry parsed from each item's otpauth:// URI.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val export = json.decodeFromString<BitwardenExport>(jsonStr)
        val entries = mutableListOf<TotpEntry>()

        for (item in export.items) {
            val totpUri = item.login?.totp ?: continue
            if (!totpUri.startsWith("otpauth://")) continue

            try {
                val entry = OtpAuthParser.parse(totpUri)
                entries.add(entry)
            } catch (e: Exception) {
                // Skip invalid entries
                continue
            }
        }

        return entries
    }

    /**
     * Parse a Bitwarden Authenticator CSV export.
     * Expected header: folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
     * The login_totp column contains the otpauth:// URI.
     */
    fun importCsv(csvStr: String): List<TotpEntry> {
        val rows = parseCsv(csvStr)
        if (rows.size < 2) return emptyList()

        val header = rows.first()
        val totpIdx = header.indexOfFirst { it.trim().equals("login_totp", ignoreCase = true) }
        if (totpIdx < 0) return emptyList()

        val entries = mutableListOf<TotpEntry>()
        for (row in rows.drop(1)) {
            if (totpIdx >= row.size) continue
            val totpUri = row[totpIdx].trim()
            if (!totpUri.startsWith("otpauth://")) continue

            try {
                val entry = OtpAuthParser.parse(totpUri)
                entries.add(entry)
            } catch (e: Exception) {
                continue
            }
        }

        return entries
    }

    /**
     * Minimal CSV parser that handles quoted fields with embedded commas and quotes.
     */
    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val current = StringBuilder()
        val fields = mutableListOf<String>()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    when (c) {
                        '"' -> {
                            if (i + 1 < text.length && text[i + 1] == '"') {
                                current.append('"')
                                i += 2
                                continue
                            }
                            inQuotes = false
                            i++
                        }
                        else -> {
                            current.append(c)
                            i++
                        }
                    }
                }
                else -> {
                    when (c) {
                        '"' -> {
                            inQuotes = true
                            i++
                        }
                        ',' -> {
                            fields.add(current.toString())
                            current.clear()
                            i++
                        }
                        '\n' -> {
                            fields.add(current.toString())
                            current.clear()
                            rows.add(fields.toList())
                            fields.clear()
                            i++
                        }
                        '\r' -> {
                            i++ // skip CR
                        }
                        else -> {
                            current.append(c)
                            i++
                        }
                    }
                }
            }
        }
        // last field/row
        if (current.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(current.toString())
            rows.add(fields.toList())
        }

        return rows
    }
}
