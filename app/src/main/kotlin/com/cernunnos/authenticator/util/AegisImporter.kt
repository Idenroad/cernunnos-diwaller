package com.cernunnos.authenticator.util

import android.util.Base64
import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom

/**
 * Import from Aegis Authenticator.
 *
 * Aegis exports a JSON file with two possible formats:
 * 1. Plain text: { "db": { "entries": [...] } }
 * 2. Encrypted: { "header": { "slots": [...] }, "db": "<base64 ciphertext>" }
 *
 * The encrypted format uses Argon2id + AES-256-GCM, same as Cernunnos.
 */
object AegisImporter {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Serializable
    private data class AegisEntry(
        val type: String = TotpConfig.TYPE_TOTP,
        val uuid: String = "",
        val name: String = "",
        val issuer: String = "",
        val info: AegisInfo = AegisInfo(),
    )

    @Serializable
    private data class AegisInfo(
        val secret: String = "",
        val algo: String = TotpConfig.ALGO_SHA1,
        val digits: Int = TotpConfig.DEFAULT_DIGITS,
        val period: Int = TotpConfig.DEFAULT_PERIOD,
        val counter: Long = 0,
    )

    /**
     * Import from Aegis JSON.
     * If encrypted, requires the passphrase.
     */
    fun import(data: String, passphrase: String? = null): List<TotpEntry> {
        val root = json.parseToJsonElement(data).jsonObject

        // Check if encrypted
        val header = root["header"]
        if (header != null) {
            // Encrypted format
            require(passphrase != null && passphrase.isNotEmpty()) {
                "This Aegis export is encrypted. A passphrase is required."
            }
            return importEncrypted(root, passphrase)
        }

        // Plain text format
        val db = root["db"]?.jsonObject
            ?: error("Invalid Aegis format: missing 'db' field")

        val entries = db["entries"]?.jsonArray
            ?: error("Invalid Aegis format: missing 'entries' field")

        return entries.map { parseEntry(it.jsonObject) }
    }

    private fun importEncrypted(root: JsonObject, passphrase: String): List<TotpEntry> {
        val header = root["header"]?.jsonObject
            ?: error("Invalid Aegis format: missing 'header' field")
        val slots = header["slots"]?.jsonArray
            ?: error("Invalid Aegis format: missing slots")

        // Find the slot that matches our passphrase
        var masterKey: ByteArray? = null
        for (slotElem in slots) {
            val slot = slotElem.jsonObject
            val type = slot["type"]?.jsonPrimitive?.contentOrNull
            if (type != "password") continue

            val salt = Base64.decode(slot["salt"]?.jsonPrimitive?.contentOrNull
                ?: error("Invalid Aegis slot: missing salt"), Base64.NO_WRAP)
            val keyParams = slot["key_params"]?.jsonObject
                ?: error("Invalid Aegis slot: missing key_params")
            val memory = keyParams["memory"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 32768
            val iterations = keyParams["n"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 3
            val parallelism = keyParams["p"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4

            val encryptedKey = slot["encrypted_key"]?.jsonObject
                ?: error("Invalid Aegis slot: missing encrypted_key")
            val keyNonce = Base64.decode(encryptedKey["nonce"]?.jsonPrimitive?.contentOrNull
                ?: error("Invalid Aegis slot: missing nonce"), Base64.NO_WRAP)
            val keyCipher = Base64.decode(
                (encryptedKey["tag"]?.jsonPrimitive?.contentOrNull ?: "") +
                (encryptedKey["ciphertext"]?.jsonPrimitive?.contentOrNull
                    ?: error("Invalid Aegis slot: missing ciphertext")), Base64.NO_WRAP)

            // Derive key from passphrase using Argon2id
            val derivedKey = Argon2id.deriveKey(passphrase.toCharArray(), salt, memory, iterations, parallelism)

            // Decrypt the master key (AES-GCM with 16-byte tag)
            try {
                masterKey = CryptoManager.decryptWithRawKey(derivedKey, keyNonce, keyCipher)
                break
            } catch (e: Exception) {
                // Try next slot
            } finally {
                // Always zero the derived key, whether success or failure
                derivedKey.fill(0)
            }
        }

        requireNotNull(masterKey) { "Failed to decrypt Aegis master key. Wrong passphrase?" }

        // Decrypt the database
        val dbCipher = root["db"]?.jsonPrimitive?.contentOrNull
            ?: error("Invalid Aegis format: missing 'db' field")
        val dbNonce = Base64.decode(root["db_nonce"]?.jsonPrimitive?.contentOrNull
            ?: error("Invalid Aegis format: missing 'db_nonce'"), Base64.NO_WRAP)
        val dbBytes = Base64.decode(dbCipher, Base64.NO_WRAP)

        // Split tag (last 16 bytes) from ciphertext
        val tag = dbBytes.copyOfRange(dbBytes.size - 16, dbBytes.size)
        val ciphertext = dbBytes.copyOfRange(0, dbBytes.size - 16)
        val combined = ciphertext + tag

        val decryptedDb = try {
            CryptoManager.decryptWithRawKey(masterKey, dbNonce, combined)
        } finally {
            masterKey.fill(0)
        }
        val dbJson = String(decryptedDb)
        val db = json.parseToJsonElement(dbJson).jsonObject
        val entries = db["entries"]?.jsonArray
            ?: error("Invalid decrypted Aegis format: missing 'entries'")

        return entries.map { parseEntry(it.jsonObject) }
    }

    private fun parseEntry(entryObj: JsonObject): TotpEntry {
        val type = entryObj["type"]?.jsonPrimitive?.contentOrNull ?: TotpConfig.TYPE_TOTP
        val name = entryObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
        val issuer = entryObj["issuer"]?.jsonPrimitive?.contentOrNull ?: ""
        val info = entryObj["info"]?.jsonObject ?: JsonObject(emptyMap())

        val secret = info["secret"]?.jsonPrimitive?.contentOrNull ?: ""
        val algo = info["algo"]?.jsonPrimitive?.contentOrNull ?: TotpConfig.ALGO_SHA1
        val digitsRaw = info["digits"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: TotpConfig.DEFAULT_DIGITS
        val digits = if (digitsRaw in 6..8) digitsRaw else TotpConfig.DEFAULT_DIGITS
        val periodRaw = info["period"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: TotpConfig.DEFAULT_PERIOD
        val period = if (periodRaw > 0) periodRaw else TotpConfig.DEFAULT_PERIOD
        val counter = info["counter"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L

        // Aegis stores secret as base32
        val secretBytes = OtpAuthParser.decodeBase32(secret)

        return TotpEntry(
            id = generateId(),
            issuer = issuer,
            label = name,
            secret = secretBytes,
            algorithm = algo.uppercase(),
            digits = digits,
            period = period,
            type = if (type == TotpConfig.TYPE_HOTP) TotpConfig.TYPE_HOTP else TotpConfig.TYPE_TOTP,
            counter = counter,
        )
    }

    private fun generateId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
