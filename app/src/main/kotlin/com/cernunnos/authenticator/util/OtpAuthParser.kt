package com.cernunnos.authenticator.util

import android.net.Uri
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.model.TotpEntry
import java.security.SecureRandom
import java.util.Base64

/**
 * Parse otpauth:// URIs (RFC 6238 / Google Authenticator format).
 * Format: otpauth://totp/LABEL?secret=BASE32&issuer=NAME&algorithm=SHA1&digits=6&period=30
 */
object OtpAuthParser {

    fun parse(uri: String): TotpEntry {
        require(uri.startsWith("otpauth://")) { "URI must start with otpauth://" }
        val parsed = Uri.parse(uri)
        require(parsed.scheme == "otpauth") { "Not an otpauth URI" }
        val type = parsed.host?.lowercase() ?: TotpConfig.TYPE_TOTP
        require(type == TotpConfig.TYPE_TOTP || type == TotpConfig.TYPE_HOTP) {
            "Only TOTP and HOTP are supported, got: $type"
        }

        // Label is the path without leading /
        val fullLabel = parsed.path?.removePrefix("/") ?: ""
        val (issuerFromLabel, label) = parseLabel(fullLabel)

        val params = parsed.queryParameterNames.associateWith { parsed.getQueryParameter(it) ?: "" }

        val issuer = params["issuer"] ?: issuerFromLabel ?: ""
        val secret = Base32Codec.decode(params["secret"] ?: error("Missing secret parameter"))
        val algorithm = params["algorithm"]?.uppercase() ?: TotpConfig.ALGO_SHA1
        require(algorithm in TotpConfig.SUPPORTED_ALGORITHMS) {
            "Unsupported algorithm: $algorithm (supported: ${TotpConfig.SUPPORTED_ALGORITHMS.joinToString()})"
        }
        val digits = params["digits"]?.toIntOrNull() ?: TotpConfig.DEFAULT_DIGITS
        require(digits in 6..8) { "Digits must be 6, 7 or 8, got: $digits" }
        val period = params["period"]?.toIntOrNull() ?: TotpConfig.DEFAULT_PERIOD
        require(period > 0) { "Period must be greater than 0, got: $period" }
        val counter = params["counter"]?.toLongOrNull() ?: 0L
        require(counter >= 0) { "Counter must be non-negative, got: $counter" }
        require(secret.size <= 128) { "Secret too long: ${secret.size} bytes (max 128)" }

        val id = generateId()

        return TotpEntry(
            id = id,
            issuer = issuer,
            label = label,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
            counter = counter,
        )
    }

    private fun parseLabel(fullLabel: String): Pair<String?, String> {
        // Format: "Issuer:label" or just "label"
        val colonIdx = fullLabel.indexOf(':')
        return if (colonIdx > 0) {
            fullLabel.substring(0, colonIdx).trim() to fullLabel.substring(colonIdx + 1).trim()
        } else {
            null to fullLabel
        }
    }

    /** Backward-compatible wrapper delegating to [Base32Codec]. */
    fun decodeBase32(encoded: String): ByteArray = Base32Codec.decode(encoded)

    /** Backward-compatible wrapper delegating to [Base32Codec]. */
    fun encodeBase32(data: ByteArray): String = Base32Codec.encode(data)

    private fun generateId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        // URL-safe Base64 without padding — equivalent to android.util.Base64
        // with URL_SAFE | NO_PADDING | NO_WRAP, but pure-JVM testable.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
