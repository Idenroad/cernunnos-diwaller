package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for AegisExporter plain JSON output.
 */
class AegisExporterTest {

    private val parser = Json { ignoreUnknownKeys = true }

    private fun entry(
        id: String = "a1",
        issuer: String = "Issuer",
        label: String = "user@example.com",
        secret: ByteArray = ByteArray(20) { it.toByte() },
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = "totp",
        counter: Long = 0L,
    ) = TotpEntry(id, issuer, label, secret, algorithm, digits, period, type = type, counter = counter)

    private fun entriesOf(out: String): JsonArray =
        parser.parseToJsonElement(out).jsonObject["db"]!!.jsonObject["entries"]!!.jsonArray

    @Test
    fun export_emptyList_returnsValidJson() {
        val out = AegisExporter.export(emptyList())
        val root = parser.parseToJsonElement(out).jsonObject
        assertEquals(1, root["version"]!!.jsonPrimitive.int)
        assertNotNull(root["header"])
        val db = root["db"]!!.jsonObject
        assertEquals(2, db["version"]!!.jsonPrimitive.int)
        assertEquals(0, db["entries"]!!.jsonArray.size)
    }

    @Test
    fun export_singleEntry_hasCorrectStructure() {
        val out = AegisExporter.export(listOf(entry()))
        val entries = entriesOf(out)
        assertEquals(1, entries.size)
        val e = entries[0].jsonObject
        assertEquals("totp", e["type"]!!.jsonPrimitive.content)
        assertEquals("a1", e["uuid"]!!.jsonPrimitive.content)
        assertEquals("user@example.com", e["name"]!!.jsonPrimitive.content)
        assertEquals("Issuer", e["issuer"]!!.jsonPrimitive.content)
        val info = e["info"]!!.jsonObject
        assertTrue(info.containsKey("secret"))
        assertEquals("SHA1", info["algo"]!!.jsonPrimitive.content)
        assertEquals(6, info["digits"]!!.jsonPrimitive.int)
        assertEquals(30, info["period"]!!.jsonPrimitive.int)
    }

    @Test
    fun export_multipleEntries_allPresent() {
        val entries = listOf(
            entry(id = "m1", issuer = "A", label = "a@x.com"),
            entry(id = "m2", issuer = "B", label = "b@x.com", secret = ByteArray(32) { it.toByte() }),
            entry(id = "m3", issuer = "C", label = "c@x.com", digits = 8, period = 60),
        )
        val out = AegisExporter.export(entries)
        val arr = entriesOf(out)
        assertEquals(3, arr.size)
        assertEquals("m1", arr[0].jsonObject["uuid"]!!.jsonPrimitive.content)
        assertEquals("m2", arr[1].jsonObject["uuid"]!!.jsonPrimitive.content)
        assertEquals("m3", arr[2].jsonObject["uuid"]!!.jsonPrimitive.content)
    }

    @Test
    fun export_hotpEntry_hasCounter() {
        val out = AegisExporter.export(listOf(entry(type = "hotp", counter = 77L)))
        val e = entriesOf(out)[0].jsonObject
        assertEquals("hotp", e["type"]!!.jsonPrimitive.content)
        assertEquals(77L, e["info"]!!.jsonObject["counter"]!!.jsonPrimitive.long)
    }

    @Test
    fun export_totpEntry_hasPeriod() {
        val out = AegisExporter.export(listOf(entry(period = 45)))
        val info = entriesOf(out)[0].jsonObject["info"]!!.jsonObject
        assertEquals(45, info["period"]!!.jsonPrimitive.int)
    }

    @Test
    fun export_outputIsParsableJson() {
        val out = AegisExporter.export(listOf(entry(), entry(id = "x2")))
        // Should not throw
        val root = parser.parseToJsonElement(out).jsonObject
        assertNotNull(root)
        assertEquals(2, entriesOf(out).size)
    }

    @Test
    fun export_entryWithSpecialChars_preserved() {
        val out = AegisExporter.export(listOf(
            entry(issuer = "Café ☕", label = "naïve\"q\"@exämple.com 🎉")
        ))
        val e = entriesOf(out)[0].jsonObject
        assertEquals("Café ☕", e["issuer"]!!.jsonPrimitive.content)
        assertEquals("naïve\"q\"@exämple.com 🎉", e["name"]!!.jsonPrimitive.content)
    }
}
