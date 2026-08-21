package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.util.OtpAuthParser.encodeBase32
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Export to 2FAS Authenticator JSON format.
 * This allows Cernunnos users to migrate to 2FAS if needed.
 *
 * Format reference: https://github.com/twofas/2fas-android
 * The 2FAS export is a JSON file with services array containing OTP entries.
 */
object TwoFasExporter {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    private data class TwoFasExport(
        val schemaVersion: Int = 1,
        val services: List<TwoFasService>,
        val groups: List<TwoFasGroup> = emptyList(),
    )

    @Serializable
    private data class TwoFasService(
        val otp: TwoFasOtp,
        val name: String,
        val icon: TwoFasIcon? = null,
    )

    @Serializable
    private data class TwoFasOtp(
        val issuer: String,
        val account: String,
        val secret: String,
        val digits: Int,
        val period: Int,
        val algorithm: String,
        val tokenType: String, // "TOTP" or "HOTP"
        val counter: Long = 0,
    )

    @Serializable
    private data class TwoFasIcon(
        val iconType: String = "none",
    )

    @Serializable
    private data class TwoFasGroup(
        val id: String,
        val name: String,
    )

    /**
     * Export entries to 2FAS JSON format.
     */
    fun export(entries: List<TotpEntry>): String {
        val services = entries.map { entry ->
            TwoFasService(
                otp = TwoFasOtp(
                    issuer = entry.issuer,
                    account = entry.label,
                    secret = encodeBase32(entry.secret),
                    digits = entry.digits,
                    period = entry.period,
                    algorithm = entry.algorithm.uppercase(),
                    tokenType = entry.type.uppercase(),
                    counter = entry.counter,
                ),
                name = entry.issuer.ifEmpty { entry.label },
                icon = TwoFasIcon(),
            )
        }

        val export = TwoFasExport(services = services)
        return json.encodeToString(export)
    }
}
