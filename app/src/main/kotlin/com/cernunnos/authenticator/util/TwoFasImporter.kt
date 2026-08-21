package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Import TOTP entries from 2FAS Authenticator JSON export.
 *
 * Format reference: https://github.com/twofas/2fas-android
 * The 2FAS export is a JSON file with a "services" array containing OTP entries.
 * Each service has an "otp" object with issuer, account, secret, algorithm, digits, period, and type.
 *
 * Encrypted 2FAS exports are not supported here — the user must export unencrypted
 * from 2FAS (Settings → Backup → Export without password).
 */
object TwoFasImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class TwoFasExport(
        val schemaVersion: Int = 1,
        val services: List<TwoFasService> = emptyList(),
        val groups: List<TwoFasGroup> = emptyList(),
    )

    @Serializable
    private data class TwoFasService(
        val otp: TwoFasOtp,
        val name: String,
        val icon: TwoFasIcon? = null,
        val groupId: String? = null,
    )

    @Serializable
    private data class TwoFasOtp(
        val issuer: String,
        val account: String,
        val secret: String,
        val algorithm: String = "SHA1",
        val digits: Int = 6,
        val period: Int = 30,
        val otpType: String = "TOTP",
    )

    @Serializable
    private data class TwoFasIcon(
        val icon: String? = null,
        val iconType: String? = null,
    )

    @Serializable
    private data class TwoFasGroup(
        val id: String,
        val name: String,
    )

    /**
     * Parse a 2FAS Authenticator JSON export.
     * @param jsonStr The JSON content of a 2FAS .2fas export file.
     * @return List of parsed TotpEntry.
     */
    fun import(jsonStr: String): List<TotpEntry> {
        val export = json.decodeFromString<TwoFasExport>(jsonStr)
        val groupMap = export.groups.associate { it.id to it.name }
        val entries = mutableListOf<TotpEntry>()

        for (service in export.services) {
            val otp = service.otp
            try {
                val secret = Base32Codec.decode(otp.secret)
                val type = when (otp.otpType.uppercase()) {
                    "HOTP" -> "hotp"
                    else -> "totp"
                }
                val algorithm = otp.algorithm.uppercase().let {
                    if (it in listOf("SHA1", "SHA256", "SHA512")) it else "SHA1"
                }
                val digits = if (otp.digits in 6..8) otp.digits else 6
                val period = if (otp.period > 0) otp.period else 30

                val id = java.util.UUID.randomUUID().toString()
                val categoryName = service.groupId?.let { groupMap[it] }
                entries.add(
                    TotpEntry(
                        id = id,
                        issuer = otp.issuer.ifBlank { service.name },
                        label = otp.account.ifBlank { service.name },
                        secret = secret,
                        algorithm = algorithm,
                        digits = digits,
                        period = period,
                        type = type,
                        categoryId = null, // Don't auto-map 2FAS groups to Cernunnos categories
                    )
                )
            } catch (e: Exception) {
                // Skip invalid entries
                continue
            }
        }

        return entries
    }
}
