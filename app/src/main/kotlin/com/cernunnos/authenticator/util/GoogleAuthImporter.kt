package com.cernunnos.authenticator.util

import android.net.Uri
import android.util.Base64
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.model.TotpEntry
import java.security.SecureRandom

/**
 * Import from Google Authenticator.
 *
 * Google Authenticator exports via "otpauth-migration://offline?data=<base64 protobuf>" QR codes.
 * The protobuf contains a list of OTP entries with secret, name, issuer, algorithm, digits, type.
 *
 * We parse the protobuf manually (wire format) without adding a protobuf dependency.
 */
object GoogleAuthImporter {

    /**
     * Parse an otpauth-migration:// URI and return a list of TOTP entries.
     */
    fun import(uri: String): List<TotpEntry> {
        require(uri.startsWith("otpauth-migration://")) { "Not a Google Authenticator migration URI" }

        val parsed = Uri.parse(uri)
        val dataParam = parsed.getQueryParameter("data")
            ?: error("Missing data parameter in migration URI")

        // The data is URL-safe base64
        val protoBytes = Base64.decode(dataParam, Base64.URL_SAFE or Base64.NO_WRAP)

        // Parse the top-level message: repeated field 1 = AuthenticatorEntry
        val entries = mutableListOf<TotpEntry>()
        val reader = ProtoReader(protoBytes)

        while (reader.hasMore()) {
            val (fieldNumber, wireType) = reader.readTag()
            if (fieldNumber == 1 && wireType == 2) {
                // Length-delimited = embedded message
                val entryBytes = reader.readBytes()
                entries.add(parseEntry(entryBytes))
            } else {
                reader.skipField(wireType)
            }
        }

        return entries
    }

    private fun parseEntry(bytes: ByteArray): TotpEntry {
        val reader = ProtoReader(bytes)
        var secret: ByteArray = ByteArray(0)
        var name = ""
        var issuer = ""
        var algorithm = TotpConfig.ALGO_SHA1
        var digits = TotpConfig.DEFAULT_DIGITS
        var period = TotpConfig.DEFAULT_PERIOD
        var type = TotpConfig.TYPE_TOTP
        var counter = 0L

        while (reader.hasMore()) {
            val (fieldNumber, wireType) = reader.readTag()
            when (fieldNumber) {
                1 -> if (wireType == 2) secret = reader.readBytes() // secret (bytes)
                2 -> if (wireType == 2) name = reader.readString()  // name
                3 -> if (wireType == 2) issuer = reader.readString() // issuer
                4 -> if (wireType == 0) {                            // algorithm (enum)
                    val algo = reader.readVarint()
                    algorithm = when (algo.toInt()) {
                        1 -> TotpConfig.ALGO_SHA1
                        2 -> TotpConfig.ALGO_SHA256
                        3 -> TotpConfig.ALGO_SHA512
                        4 -> "MD5"
                        else -> TotpConfig.ALGO_SHA1
                    }
                }
                5 -> if (wireType == 0) {                            // digits
                    val d = reader.readVarint().toInt()
                    if (d == 6 || d == 8) digits = d
                }
                6 -> if (wireType == 0) {                            // type (enum)
                    val t = reader.readVarint().toInt()
                    type = if (t == 1) TotpConfig.TYPE_HOTP else TotpConfig.TYPE_TOTP
                }
                7 -> if (wireType == 0) counter = reader.readVarint() // counter (HOTP)
                else -> reader.skipField(wireType)
            }
        }

        // Google Auth stores the secret as raw bytes, we need base32 for our format
        // But we store as raw bytes too, so we can use it directly

        // Extract issuer from name if issuer is empty (Google sometimes puts "Issuer:Name" in name)
        if (issuer.isEmpty() && name.contains(":")) {
            val parts = name.split(":", limit = 2)
            issuer = parts[0].trim()
            name = parts[1].trim()
        }

        return TotpEntry(
            id = generateId(),
            issuer = issuer,
            label = name,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
            type = type,
            counter = counter,
        )
    }

    private fun generateId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

/**
 * Minimal protobuf wire format reader.
 */
private class ProtoReader(private val data: ByteArray) {
    private var pos = 0

    fun hasMore(): Boolean = pos < data.size

    fun readTag(): Pair<Int, Int> {
        val tag = readVarint().toInt()
        val fieldNumber = tag shr 3
        val wireType = tag and 0x07
        return fieldNumber to wireType
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        var iterations = 0
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            iterations++
            // A 64-bit varint has at most 10 continuation bytes; reject malformed input
            if (iterations > 10) error("Malformed protobuf: varint too long")
        }
        return result
    }

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        require(length >= 0) { "Invalid negative length in protobuf: $length" }
        // Use Long arithmetic to avoid integer overflow when length is close to Int.MAX_VALUE
        require(pos.toLong() + length.toLong() <= data.size.toLong()) {
            "Protobuf field extends beyond buffer: pos=$pos, length=$length, size=${data.size}"
        }
        // Sanity check: reject absurdly large lengths even if within bounds
        require(length <= data.size) { "Protobuf length $length exceeds buffer size ${data.size}" }
        val bytes = data.copyOfRange(pos, pos + length)
        pos += length
        return bytes
    }

    fun readString(): String {
        val bytes = readBytes()
        return String(bytes, Charsets.UTF_8)
    }

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint() // varint
            1 -> {
                require(pos + 8 <= data.size) { "Protobuf 64-bit field extends beyond buffer" }
                pos += 8
            }
            2 -> readBytes()  // length-delimited (already bounds-checked)
            5 -> {
                require(pos + 4 <= data.size) { "Protobuf 32-bit field extends beyond buffer" }
                pos += 4
            }
            // else: unknown wire type, can't skip safely
        }
    }
}
