package com.cernunnos.authenticator.util

import android.util.Xml
import com.cernunnos.authenticator.data.model.TotpEntry
import org.xmlpull.v1.XmlPullParser

/**
 * Import TOTP entries from FreeOTP / FreeOTP+ backup.
 *
 * FreeOTP stores entries in an XML format (key-value pairs) when exported.
 * The format is a list of <token> elements with attributes:
 * - "issuer": issuer name
 * - "label": account label
 * - "secret": base32/hex-encoded secret
 * - "algo": algorithm (SHA1, SHA256, SHA512)
 * - "digits": number of digits
 * - "period": period in seconds
 * - "type": "totp" or "hotp"
 * - "counter": HOTP counter
 *
 * FreeOTP+ uses a similar format with possible additional fields.
 */
object FreeOtpImporter {

    /**
     * Parse a FreeOTP XML export.
     * @param xml XML content of the FreeOTP export.
     * @return List of parsed TotpEntry.
     */
    fun import(xml: String): List<TotpEntry> {
        val entries = mutableListOf<TotpEntry>()
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "token") {
                try {
                    val entry = parseToken(parser)
                    if (entry != null) entries.add(entry)
                } catch (e: Exception) {
                    // Skip invalid tokens
                }
            }
            eventType = parser.next()
        }

        return entries
    }

    private fun parseToken(parser: XmlPullParser): TotpEntry? {
        val issuer = parser.getAttributeValue(null, "issuer") ?: ""
        val label = parser.getAttributeValue(null, "label")
            ?: parser.getAttributeValue(null, "account") ?: ""
        val secretStr = parser.getAttributeValue(null, "secret") ?: return null
        val algo = parser.getAttributeValue(null, "algo")
            ?: parser.getAttributeValue(null, "algorithm") ?: "SHA1"
        val digitsStr = parser.getAttributeValue(null, "digits") ?: "6"
        val periodStr = parser.getAttributeValue(null, "period") ?: "30"
        val type = (parser.getAttributeValue(null, "type") ?: "totp").lowercase()
        val counterStr = parser.getAttributeValue(null, "counter") ?: "0"

        if (secretStr.isBlank()) return null

        val secret = try {
            Base32Codec.decode(secretStr)
        } catch (e: Exception) {
            // Try hex decoding as fallback
            try {
                secretStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (e2: Exception) {
                return null
            }
        }

        val algorithm = algo.uppercase().let {
            if (it in listOf("SHA1", "SHA256", "SHA512")) it else "SHA1"
        }
        val digits = digitsStr.toIntOrNull()?.let { if (it in 6..8) it else 6 } ?: 6
        val period = periodStr.toIntOrNull()?.let { if (it > 0) it else 30 } ?: 30
        val counter = counterStr.toLongOrNull() ?: 0L

        val id = java.util.UUID.randomUUID().toString()
        return TotpEntry(
            id = id,
            issuer = issuer.ifBlank { "FreeOTP" },
            label = label,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = if (type == "hotp") "hotp" else "totp",
            counter = counter,
        )
    }
}
