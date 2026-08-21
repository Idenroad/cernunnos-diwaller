package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64 as JBase64

private object Base64Compat {
    fun encode(bytes: ByteArray): String = JBase64.getEncoder().withoutPadding().encodeToString(bytes)
    fun decode(str: String): ByteArray = JBase64.getDecoder().decode(str)
}

/**
 * Encrypted export/import of TOTP entries.
 *
 * Format v1 (current): v1:sha256(payload):base64(salt):base64(iv):base64(ciphertext)
 * Format v0 (legacy):  base64(salt):base64(iv):base64(ciphertext)  — no checksum, no version
 *
 * The ciphertext is AES-256-GCM encrypted JSON of the entries.
 * The checksum detects truncation or corruption of the export file.
 */
object ExportImport {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private const val CURRENT_VERSION = "v1"

    @Serializable
    private data class ExportEntry(
        val id: String,
        val issuer: String,
        val label: String,
        val secret: String, // base64
        val algorithm: String,
        val digits: Int,
        val period: Int,
        val type: String = TotpConfig.TYPE_TOTP,
        val counter: Long = 0L,
        val categoryId: String? = null,
        val favorite: Boolean = false,
        val iconName: String? = null,
        val customIconUri: String? = null,
        val pin: String? = null,
    )

    fun export(entries: List<TotpEntry>, passphrase: String): String {
        // Convert to CharArray, export, and zero the array.
        // The String itself remains in memory (immutable), but this at least
        // avoids leaving the CharArray copy around.
        val chars = passphrase.toCharArray()
        return try {
            export(entries, chars)
        } finally {
            chars.fill(0.toChar())
        }
    }

    /**
     * Export with CharArray passphrase — the array is zeroed after use.
     * Prefer this overload to avoid creating immutable String copies in memory.
     */
    fun export(entries: List<TotpEntry>, passphrase: CharArray): String {
        try {
            val exportEntries = entries.map {
                ExportEntry(
                    id = it.id,
                    issuer = it.issuer,
                    label = it.label,
                    secret = Base64Compat.encode(it.secret),
                    algorithm = it.algorithm,
                    digits = it.digits,
                    period = it.period,
                    type = it.type,
                    counter = it.counter,
                    categoryId = it.categoryId,
                    favorite = it.favorite,
                    iconName = it.iconName,
                    customIconUri = it.customIconUri,
                    pin = it.pin,
                )
            }
            val jsonBytes = json.encodeToString(exportEntries).toByteArray()
            val salt = Argon2id.generateSalt()
            val encrypted = CryptoManager.encrypt(jsonBytes, passphrase, salt)
            val payload = "${Base64Compat.encode(salt)}:" +
                    "${Base64Compat.encode(encrypted.iv)}:" +
                    Base64Compat.encode(encrypted.ciphertext)
            val checksum = sha256Base64(payload)
            return "$CURRENT_VERSION:$checksum:$payload"
        } finally {
            passphrase.fill(0.toChar())
        }
    }

    fun import(data: String, passphrase: String): List<TotpEntry> {
        val chars = passphrase.toCharArray()
        return try {
            import(data, chars)
        } finally {
            chars.fill(0.toChar())
        }
    }

    /**
     * Import with CharArray passphrase — the array is zeroed after use.
     * Prefer this overload to avoid creating immutable String copies in memory.
     */
    fun import(data: String, passphrase: CharArray): List<TotpEntry> {
        try {
            val trimmed = data.trim()
            if (trimmed.isEmpty()) error("Import file is empty")
            val parts = trimmed.split(":")

            val (salt, iv, ciphertext) = when {
                // v1 format: v1:checksum:salt:iv:ciphertext
                parts.size == 5 && parts[0] == CURRENT_VERSION -> {
                    val expectedChecksum = parts[1]
                    val payload = parts.subList(2, 5).joinToString(":")
                    val actualChecksum = sha256Base64(payload)
                    require(actualChecksum == expectedChecksum) {
                        "Export file is corrupted (checksum mismatch)"
                    }
                    Triple(
                        Base64Compat.decode(parts[2]),
                        Base64Compat.decode(parts[3]),
                        Base64Compat.decode(parts[4]),
                    )
                }
                // v0 legacy format: salt:iv:ciphertext (no checksum, no version)
                parts.size == 3 -> {
                    Triple(
                        Base64Compat.decode(parts[0]),
                        Base64Compat.decode(parts[1]),
                        Base64Compat.decode(parts[2]),
                    )
                }
                else -> error("Invalid export format: expected 3 or 5 parts, got ${parts.size}")
            }

            val encrypted = CryptoManager.EncryptedData(salt, iv, ciphertext)
            val decrypted = try {
                CryptoManager.decrypt(encrypted, passphrase)
            } catch (e: Exception) {
                error("Invalid passphrase or corrupted data")
            }
            val exportEntries = try {
                json.decodeFromString<List<ExportEntry>>(String(decrypted))
            } catch (e: Exception) {
                error("Invalid JSON format in export file — may be from an incompatible version")
            }

            return exportEntries.map {
                TotpEntry(
                    id = it.id,
                    issuer = it.issuer,
                    label = it.label,
                    secret = Base64Compat.decode(it.secret),
                    algorithm = it.algorithm,
                    digits = if (it.digits in 6..8) it.digits else TotpConfig.DEFAULT_DIGITS,
                    period = if (it.period > 0) it.period else TotpConfig.DEFAULT_PERIOD,
                    type = it.type,
                    counter = it.counter,
                    categoryId = it.categoryId,
                    favorite = it.favorite,
                    iconName = it.iconName,
                    customIconUri = it.customIconUri,
                    pin = it.pin,
                )
            }
        } finally {
            passphrase.fill(0.toChar())
        }
    }

    private fun sha256Base64(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return Base64Compat.encode(digest)
    }
}
