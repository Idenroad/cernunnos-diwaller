package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for TwoFasExporter JSON output.
 */
class TwoFasExporterTest {

    private val parser = Json { ignoreUnknownKeys = true }

    private fun entry(
        id: String = "t1",
        issuer: String = "Issuer",
        label: String = "user@example.com",
        secret: ByteArray = ByteArray(20) { it.toByte() },
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
        type: String = "totp",
        counter: Long = 0L,
    ) = TotpEntry(id, issuer, label, secret, algorithm, digits, period, type = type, counter = counter)

    private fun servicesOf(out: String): JsonArray =
        parser.parseToJsonElement(out).jsonObject["services"]!!.jsonArray

    @Test
    fun export_emptyList_returnsValidJson() {
        val out = TwoFasExporter.export(emptyList())
        val root = parser.parseToJsonElement(out).jsonObject
        assertEquals(1, root["schemaVersion"]!!.jsonPrimitive.int)
        assertEquals(0, root["services"]!!.jsonArray.size)
    }

    @Test
    fun export_singleEntry_hasCorrectStructure() {
        val out = TwoFasExporter.export(listOf(entry()))
        val services = servicesOf(out)
        assertEquals(1, services.size)
        val svc = services[0].jsonObject
        val otp = svc["otp"]!!.jsonObject
        assertEquals("Issuer", otp["issuer"]!!.jsonPrimitive.content)
        assertEquals("user@example.com", otp["account"]!!.jsonPrimitive.content)
        assertTrue(otp.containsKey("secret"))
        assertEquals(6, otp["digits"]!!.jsonPrimitive.int)
        assertEquals(30, otp["period"]!!.jsonPrimitive.int)
        assertEquals("SHA1", otp["algorithm"]!!.jsonPrimitive.content)
        assertEquals("TOTP", otp["tokenType"]!!.jsonPrimitive.content)
        assertEquals(0L, otp["counter"]!!.jsonPrimitive.long)
        assertEquals("Issuer", svc["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun export_multipleEntries_allPresent() {
        val entries = listOf(
            entry(id = "m1", issuer = "A", label = "a@x.com"),
            entry(id = "m2", issuer = "B", label = "b@x.com", secret = ByteArray(32) { it.toByte() }),
            entry(id = "m3", issuer = "C", label = "c@x.com", digits = 8, period = 60),
        )
        val out = TwoFasExporter.export(entries)
        val arr = servicesOf(out)
        assertEquals(3, arr.size)
        assertEquals("A", arr[0].jsonObject["otp"]!!.jsonObject["issuer"]!!.jsonPrimitive.content)
        assertEquals("B", arr[1].jsonObject["otp"]!!.jsonObject["issuer"]!!.jsonPrimitive.content)
        assertEquals("C", arr[2].jsonObject["otp"]!!.jsonObject["issuer"]!!.jsonPrimitive.content)
    }

    @Test
    fun export_outputIsParsableJson() {
        val out = TwoFasExporter.export(listOf(entry(), entry(id = "x2")))
        val root = parser.parseToJsonElement(out).jsonObject
        assertNotNull(root)
        assertEquals(2, servicesOf(out).size)
    }

    @Test
    fun export_entryWithSpecialChars_preserved() {
        val out = TwoFasExporter.export(listOf(
            entry(issuer = "Café ☕", label = "naïve\"q\"@exämple.com 🎉")
        ))
        val otp = servicesOf(out)[0].jsonObject["otp"]!!.jsonObject
        assertEquals("Café ☕", otp["issuer"]!!.jsonPrimitive.content)
        assertEquals("naïve\"q\"@exämple.com 🎉", otp["account"]!!.jsonPrimitive.content)
    }
}
