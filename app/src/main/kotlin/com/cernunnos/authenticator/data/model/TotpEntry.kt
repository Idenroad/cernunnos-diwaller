package com.cernunnos.authenticator.data.model

import com.cernunnos.authenticator.constants.*
import kotlinx.serialization.Serializable

@Serializable
data class TotpEntry(
    val id: String,
    val issuer: String,
    val label: String,
    val secret: ByteArray,
    val algorithm: String = TotpConfig.ALGO_SHA1,
    val digits: Int = TotpConfig.DEFAULT_DIGITS,
    val period: Int = TotpConfig.DEFAULT_PERIOD,
    val categoryId: String? = null,
    val favorite: Boolean = false,
    val type: String = TotpConfig.TYPE_TOTP, // "totp" or "hotp"
    val counter: Long = 0L, // HOTP counter
    val iconName: String? = null, // optional custom Material icon name
    val customIconUri: String? = null, // optional custom image URI (from gallery)
    val pin: String? = null, // mOTP PIN (only used for type="motp")
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TotpEntry) return false
        return id == other.id &&
            issuer == other.issuer &&
            label == other.label &&
            algorithm == other.algorithm &&
            digits == other.digits &&
            period == other.period &&
            categoryId == other.categoryId &&
            favorite == other.favorite &&
            type == other.type &&
            counter == other.counter &&
            iconName == other.iconName &&
            customIconUri == other.customIconUri &&
            pin == other.pin
    }

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class TotpEntryMeta(
    val id: String,
    val issuer: String,
    val label: String,
    val algorithm: String = TotpConfig.ALGO_SHA1,
    val digits: Int = TotpConfig.DEFAULT_DIGITS,
    val period: Int = TotpConfig.DEFAULT_PERIOD,
    val categoryId: String? = null,
    val favorite: Boolean = false,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
)
