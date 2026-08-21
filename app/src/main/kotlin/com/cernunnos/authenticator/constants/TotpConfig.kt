package com.cernunnos.authenticator.constants

/**
 * TOTP/HOTP configuration constants.
 */
object TotpConfig {
    // Types
    const val TYPE_TOTP = "totp"
    const val TYPE_HOTP = "hotp"
    const val TYPE_STEAM = "steam"
    const val TYPE_MOTP = "motp"
    const val TYPE_YANDEX = "yandex"

    // Algorithms
    const val ALGO_SHA1 = "SHA1"
    const val ALGO_SHA256 = "SHA256"
    const val ALGO_SHA512 = "SHA512"
    const val ALGO_MD5 = "MD5"
    const val DEFAULT_ALGORITHM = ALGO_SHA1

    // Defaults
    const val DEFAULT_DIGITS = 6
    const val DEFAULT_PERIOD = 30 // seconds

    // mOTP uses 10-second windows
    const val MOTP_PERIOD = 10

    // Steam: 5 alphanumeric characters
    const val STEAM_DIGITS = 5
    val STEAM_CHARSET = "23456789BCDFGHJKMNPQRTVWXY".toCharArray()

    // Yandex: 8 characters from a custom charset
    const val YANDEX_DIGITS = 8
    val YANDEX_CHARSET = "0123456789abcdef".toCharArray()

    // Supported algorithms
    val SUPPORTED_ALGORITHMS = setOf(ALGO_SHA1, ALGO_SHA256, ALGO_SHA512)

    // All supported OTP types
    val SUPPORTED_TYPES = setOf(TYPE_TOTP, TYPE_HOTP, TYPE_STEAM, TYPE_MOTP, TYPE_YANDEX)
}
