package com.cernunnos.authenticator.totp

import com.cernunnos.authenticator.constants.*
import java.nio.ByteBuffer
import java.security.Key
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 TOTP generator + Steam/mOTP/Yandex variants.
 */
object TotpGenerator {

    private val SUPPORTED_ALGORITHMS = TotpConfig.SUPPORTED_ALGORITHMS

    private fun normalizeAlgorithm(algorithm: String): String {
        val upper = algorithm.uppercase()
        require(upper in SUPPORTED_ALGORITHMS) {
            "Unsupported algorithm: $algorithm. Supported: $SUPPORTED_ALGORITHMS"
        }
        return upper
    }

    fun generate(
        secret: ByteArray,
        time: Long = System.currentTimeMillis() / 1000,
        step: Int = TotpConfig.DEFAULT_PERIOD,
        digits: Int = TotpConfig.DEFAULT_DIGITS,
        algorithm: String = TotpConfig.DEFAULT_ALGORITHM,
    ): String {
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        require(step > 0) { "Step must be positive, got: $step" }
        require(digits in 6..8) { "Digits must be 6, 7 or 8, got: $digits" }
        val algo = normalizeAlgorithm(algorithm)

        val counter = time / step
        val hmacAlgo = "Hmac$algo"
        val hmac = Mac.getInstance(hmacAlgo)
        hmac.init(SecretKeySpec(secret, hmacAlgo))
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = hmac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val mod = 10.0.pow(digits).toInt()
        val code = binary % mod
        return code.toString().padStart(digits, '0')
    }

    fun remainingSeconds(step: Int = TotpConfig.DEFAULT_PERIOD, time: Long = System.currentTimeMillis() / 1000): Int {
        require(step > 0) { "Step must be positive, got: $step" }
        return step - (time % step).toInt()
    }

    /**
     * RFC 4226 HOTP generator.
     * Uses a counter instead of time.
     */
    fun generateHotp(
        secret: ByteArray,
        counter: Long,
        digits: Int = TotpConfig.DEFAULT_DIGITS,
        algorithm: String = TotpConfig.DEFAULT_ALGORITHM,
    ): String {
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        require(digits in 6..8) { "Digits must be 6, 7 or 8, got: $digits" }
        val algo = normalizeAlgorithm(algorithm)

        val hmacAlgo = "Hmac$algo"
        val hmac = Mac.getInstance(hmacAlgo)
        hmac.init(SecretKeySpec(secret, hmacAlgo))
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = hmac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)

        val mod = 10.0.pow(digits).toInt()
        val code = binary % mod
        return code.toString().padStart(digits, '0')
    }

    // ── Steam Guard ──

    /**
     * Steam Guard TOTP generator.
     *
     * Uses HMAC-SHA1 with the same time-based counter as standard TOTP (30s),
     * but extracts 5 characters from a custom 26-character alphabet:
     * "23456789BCDFGHJKMNPQRTVWXY" (no vowels, no 0/1, no confusing chars).
     *
     * The algorithm:
     * 1. Compute HMAC-SHA1(secret, counter) → 20 bytes
     * 2. Extract a 4-byte big-endian value at offset (hash[19] & 0x0F)
     * 3. For each of the 5 characters, take value % 26, then value /= 26
     *
     * This is the same approach as the reference Steam Guard implementation.
     */
    fun generateSteam(
        secret: ByteArray,
        time: Long = System.currentTimeMillis() / 1000,
    ): String {
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        val counter = time / TotpConfig.DEFAULT_PERIOD // Steam uses 30s

        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(SecretKeySpec(secret, "HmacSHA1"))
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = hmac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        // Extract 4-byte big-endian value (same as standard TOTP), as unsigned Long
        var binary: Long = ((hash[offset].toInt() and 0x7f).toLong() shl 24) or
                ((hash[offset + 1].toInt() and 0xff).toLong() shl 16) or
                ((hash[offset + 2].toInt() and 0xff).toLong() shl 8) or
                (hash[offset + 3].toInt() and 0xff).toLong()

        val charset = TotpConfig.STEAM_CHARSET
        val sb = StringBuilder(TotpConfig.STEAM_DIGITS)

        for (i in 0 until TotpConfig.STEAM_DIGITS) {
            sb.append(charset[(binary % charset.size).toInt()])
            binary /= charset.size
        }

        return sb.toString()
    }

    // ── Yandex ──

    /**
     * Yandex TOTP generator.
     *
     * Uses HMAC-SHA1 with a custom 16-character hex alphabet "0123456789abcdef"
     * and produces 8 characters. Same extraction approach as Steam but with
     * a different charset and length.
     */
    fun generateYandex(
        secret: ByteArray,
        time: Long = System.currentTimeMillis() / 1000,
        step: Int = TotpConfig.DEFAULT_PERIOD,
    ): String {
        require(secret.isNotEmpty()) { "Secret cannot be empty" }
        require(step > 0) { "Step must be positive, got: $step" }
        val counter = time / step

        val hmac = Mac.getInstance("HmacSHA1")
        hmac.init(SecretKeySpec(secret, "HmacSHA1"))
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val hash = hmac.doFinal(counterBytes)

        val offset = (hash[hash.size - 1].toInt() and 0x0f)
        var binary: Long = ((hash[offset].toInt() and 0x7f).toLong() shl 24) or
                ((hash[offset + 1].toInt() and 0xff).toLong() shl 16) or
                ((hash[offset + 2].toInt() and 0xff).toLong() shl 8) or
                (hash[offset + 3].toInt() and 0xff).toLong()

        val charset = TotpConfig.YANDEX_CHARSET
        val sb = StringBuilder(TotpConfig.YANDEX_DIGITS)

        for (i in 0 until TotpConfig.YANDEX_DIGITS) {
            sb.append(charset[(binary % charset.size).toInt()])
            binary /= charset.size
        }

        return sb.toString()
    }

    // ── mOTP (Mobile OTP) ──

    /**
     * mOTP (Mobile OTP) generator.
     *
     * Uses MD5 instead of HMAC. The code is derived from:
     *   MD5(secret_hex + pin + time_window)
     * where time_window = floor(unix_time / 10).
     *
     * The first 6 digits of the MD5 hex digest are the OTP code.
     *
     * @param secretHex The secret as a hex string (not base32).
     * @param pin The user's PIN (typically 4 digits).
     * @param time Unix timestamp in seconds.
     */
    fun generateMotp(
        secretHex: String,
        pin: String,
        time: Long = System.currentTimeMillis() / 1000,
    ): String {
        require(secretHex.isNotEmpty()) { "Secret cannot be empty" }
        require(pin.isNotEmpty()) { "PIN cannot be empty" }

        val epoch = time / TotpConfig.MOTP_PERIOD
        val input = secretHex.lowercase() + pin + epoch.toString()

        val md5 = MessageDigest.getInstance("MD5")
        val hashBytes = md5.digest(input.toByteArray(Charsets.UTF_8))

        // Convert to hex string and take first 6 digits
        val hexFull = hashBytes.joinToString("") { "%02x".format(it) }

        // Extract 6 digits from the hex hash
        // mOTP takes the first 6 characters of the MD5 hex that are digits,
        // but the common implementation takes the first 6 hex chars and
        // converts them to a number, then takes modulo 10^6
        val first6Hex = hexFull.take(6)
        val num = first6Hex.toLong(16)
        return (num % 1_000_000L).toString().padStart(6, '0')
    }

    // ── Dispatch function ──

    /**
     * Generate a code for any OTP type.
     * Dispatches to the appropriate generator based on [type].
     *
     * For mOTP, [secret] should be the hex secret as bytes and [pin] should be
     * passed via the algorithm field (hack: we use algorithm to carry the PIN).
     * Actually, mOTP needs special handling — callers should use generateMotp directly.
     */
    fun generateForType(
        secret: ByteArray,
        type: String,
        time: Long = System.currentTimeMillis() / 1000,
        step: Int = TotpConfig.DEFAULT_PERIOD,
        digits: Int = TotpConfig.DEFAULT_DIGITS,
        algorithm: String = TotpConfig.DEFAULT_ALGORITHM,
        counter: Long = 0L,
        pin: String = "",
        secretHex: String = "",
    ): String {
        return when (type) {
            TotpConfig.TYPE_STEAM -> generateSteam(secret, time)
            TotpConfig.TYPE_YANDEX -> generateYandex(secret, time, step)
            TotpConfig.TYPE_MOTP -> generateMotp(secretHex.ifBlank { secret.joinToString("") { "%02x".format(it) } }, pin, time)
            TotpConfig.TYPE_HOTP -> generateHotp(secret, counter, digits, algorithm)
            else -> generate(secret, time, step, digits, algorithm)
        }
    }
}
