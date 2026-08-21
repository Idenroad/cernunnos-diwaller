package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry

/**
 * Import TOTP entries from LastPass Authenticator CSV export.
 *
 * LastPass Authenticator exports a CSV with columns:
 *   issuer,label,secret,digits,type,algorithm,period
 *
 * Or alternatively a simpler format:
 *   name,username,secret
 *
 * This importer auto-detects the column layout from the header row.
 */
object LastPassImporter {

    /**
     * Parse a LastPass Authenticator CSV export.
     * @param csv CSV content.
     * @return List of parsed TotpEntry.
     */
    fun import(csv: String): List<TotpEntry> {
        val rows = parseCsv(csv)
        if (rows.size < 2) return emptyList()

        val header = rows.first().map { it.trim().lowercase() }
        val entries = mutableListOf<TotpEntry>()

        // Find column indices
        val issuerIdx = header.indexOfFirst { it in listOf("issuer", "name", "site") }
        val labelIdx = header.indexOfFirst { it in listOf("label", "username", "account") }
        val secretIdx = header.indexOfFirst { it in listOf("secret", "secretkey", "secret_key") }
        val digitsIdx = header.indexOfFirst { it == "digits" }
        val typeIdx = header.indexOfFirst { it in listOf("type", "otptype", "otp_type") }
        val algoIdx = header.indexOfFirst { it in listOf("algorithm", "algo") }
        val periodIdx = header.indexOfFirst { it == "period" }

        if (secretIdx < 0) return emptyList()

        for (row in rows.drop(1)) {
            try {
                val secretStr = row.getOrNull(secretIdx)?.trim() ?: continue
                if (secretStr.isBlank()) continue
                val secret = Base32Codec.decode(secretStr)

                val issuer = if (issuerIdx >= 0) row.getOrNull(issuerIdx)?.trim() ?: "" else ""
                val label = if (labelIdx >= 0) row.getOrNull(labelIdx)?.trim() ?: "" else ""
                val digits = if (digitsIdx >= 0) row.getOrNull(digitsIdx)?.toIntOrNull() ?: 6 else 6
                val type = if (typeIdx >= 0) row.getOrNull(typeIdx)?.trim()?.lowercase() ?: "totp" else "totp"
                val algorithm = if (algoIdx >= 0) row.getOrNull(algoIdx)?.trim()?.uppercase() ?: "SHA1" else "SHA1"
                val period = if (periodIdx >= 0) row.getOrNull(periodIdx)?.toIntOrNull() ?: 30 else 30

                val digitsSafe = if (digits in 6..8) digits else 6
                val periodSafe = if (period > 0) period else 30
                val algorithmSafe = if (algorithm in listOf("SHA1", "SHA256", "SHA512")) algorithm else "SHA1"
                val typeSafe = if (type == "hotp") "hotp" else "totp"

                val id = java.util.UUID.randomUUID().toString()
                entries.add(
                    TotpEntry(
                        id = id,
                        issuer = issuer.ifBlank { "LastPass" },
                        label = label,
                        secret = secret,
                        algorithm = algorithmSafe,
                        digits = digitsSafe,
                        period = periodSafe,
                        type = typeSafe,
                    )
                )
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
                        '"' -> { inQuotes = true; i++ }
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
                        '\r' -> { i++ }
                        else -> { current.append(c); i++ }
                    }
                }
            }
        }
        if (current.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(current.toString())
            rows.add(fields.toList())
        }
        return rows
    }
}
