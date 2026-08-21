package com.cernunnos.authenticator.util

/**
 * Pure-JVM Base32 (RFC 4648) encoder/decoder using the standard alphabet
 * "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567". Extracted from OtpAuthParser so it can
 * be unit-tested on the JVM without Android dependencies.
 */
object Base32Codec {

    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val LOOKUP = ALPHABET.mapIndexed { idx, c -> c to idx }.toMap()

    /**
     * Decode a Base32 string. Case-insensitive; spaces, dashes and padding
     * ('=') are stripped before decoding. Throws on invalid characters.
     */
    fun decode(encoded: String): ByteArray {
        val clean = encoded.uppercase().replace(" ", "").replace("-", "").replace("=", "")
        val bits = StringBuilder()
        for (c in clean) {
            val v = LOOKUP[c] ?: error("Invalid Base32 character: $c")
            bits.append(v.toString(2).padStart(5, '0'))
        }
        // Truncate to multiple of 8
        val usableBits = bits.length - (bits.length % 8)
        val result = ByteArray(usableBits / 8)
        for (i in result.indices) {
            val byteStr = bits.substring(i * 8, i * 8 + 8)
            result[i] = byteStr.toUByte(2).toByte()
        }
        return result
    }

    /**
     * Encode bytes to a Base32 string (no padding).
     */
    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val idx = (buffer shr (bitsLeft - 5)) and 0x1f
                sb.append(ALPHABET[idx])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            val idx = (buffer shl (5 - bitsLeft)) and 0x1f
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }
}
