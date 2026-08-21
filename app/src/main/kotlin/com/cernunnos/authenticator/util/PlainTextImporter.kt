package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Import TOTP entries from a plain text file containing one otpauth:// URI per line.
 *
 * This is the simplest possible format — just a list of otpauth:// URIs, one per line.
 * Lines starting with '#' are treated as comments and ignored.
 * Empty lines are ignored.
 *
 * This format is compatible with:
 * - Manual exports from various authenticator apps
 * - WinAuth exports (one URI per line)
 * - Generic otpauth:// URI lists
 */
object PlainTextImporter {

    /**
     * Parse a plain text file containing otpauth:// URIs.
     * @param text One or more otpauth:// URIs, one per line.
     * @return List of parsed TotpEntry.
     */
    fun import(text: String): List<TotpEntry> {
        val entries = mutableListOf<TotpEntry>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (!trimmed.startsWith("otpauth://")) continue
            try {
                entries.add(OtpAuthParser.parse(trimmed))
            } catch (e: Exception) {
                // Skip invalid lines
                continue
            }
        }
        return entries
    }
}
