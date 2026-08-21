package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.constants.*
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.util.OtpAuthParser.encodeBase32
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Export to Aegis Authenticator plain JSON format.
 * This allows Cernunnos users to migrate to Aegis if needed.
 *
 * Format reference: https://github.com/beemdevelopment/Aegis
 */
object AegisExporter {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    @Serializable
    private data class AegisExport(
        val version: Int = 1,
        val header: AegisHeader = AegisHeader(),
        val db: AegisDb,
    )

    @Serializable
    private data class AegisHeader(
        val slots: String? = null,
        val params: String? = null,
    )

    @Serializable
    private data class AegisDb(
        val version: Int = 2,
        val entries: List<AegisEntry>,
    )

    @Serializable
    private data class AegisEntry(
        val type: String,
        val uuid: String,
        val name: String,
        val issuer: String,
        val info: AegisInfo,
    )

    @Serializable
    private data class AegisInfo(
        val secret: String,
        val algo: String,
        val digits: Int,
        val period: Int = TotpConfig.DEFAULT_PERIOD,
        val counter: Long = 0,
    )

    /**
     * Export entries to Aegis plain JSON format.
     */
    fun export(entries: List<TotpEntry>): String {
        val aegisEntries = entries.map { entry ->
            AegisEntry(
                type = entry.type,
                uuid = entry.id,
                name = entry.label,
                issuer = entry.issuer,
                info = AegisInfo(
                    secret = encodeBase32(entry.secret),
                    algo = entry.algorithm.uppercase(),
                    digits = entry.digits,
                    period = entry.period,
                    counter = entry.counter,
                ),
            )
        }

        val export = AegisExport(
            db = AegisDb(version = 2, entries = aegisEntries),
        )

        return json.encodeToString(export)
    }
}
