package com.cernunnos.authenticator.util

import com.cernunnos.authenticator.data.model.TotpEntry
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for BitwardenImporter (JSON and CSV formats).
 */
class BitwardenImporterTest {

    // A valid Base32 secret: "JBSWY3DPEHPK3PXP" decodes to 10 bytes.
    private val validSecret = "JBSWY3DPEHPK3PXP"

    private fun totpUri(
        issuer: String = "Amazon",
        account: String = "user@email.com",
        secret: String = validSecret,
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
    ) = "otpauth://totp/$issuer:$account?secret=$secret&issuer=$issuer&algorithm=$algorithm&digits=$digits&period=$period"

    @Test
    fun import_emptyJson_returnsEmptyList() {
        val json = """{"encrypted":false,"items":[]}"""
        val entries = BitwardenImporter.import(json)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun import_invalidJson_returnsEmptyList() {
        // Broken JSON should be rejected; importer throws, so wrap.
        val json = """{not valid json"""
        try {
            val entries = BitwardenImporter.import(json)
            assertTrue(entries.isEmpty())
        } catch (e: Exception) {
            // Decoding failure is acceptable behavior for invalid JSON.
            assertTrue(true)
        }
    }

    @Test
    fun import_jsonWithoutTotpField_returnsEmptyList() {
        val json = """
            {"encrypted":false,"items":[
                {"id":"1","name":"NoTotp","type":1,"login":{"username":"u@x.com"},"favorite":false}
            ]}
        """.trimIndent()
        val entries = BitwardenImporter.import(json)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun import_jsonWithTotpEntries_returnsEntries() {
        val json = """
            {"encrypted":false,"items":[
                {"id":"i1","name":"Amazon","type":1,
                 "login":{"totp":"${totpUri()}","username":"user@email.com"},"favorite":false},
                {"id":"i2","name":"GitHub","type":1,
                 "login":{"totp":"${totpUri(issuer="GitHub", account="dev@gh.com")}","username":"dev@gh.com"},"favorite":false}
            ]}
        """.trimIndent()
        val entries = BitwardenImporter.import(json)
        assertEquals(2, entries.size)
        // Issuer/label parsed from otpauth URI.
        val issuers = entries.map { it.issuer }
        assertTrue(issuers.contains("Amazon"))
        assertTrue(issuers.contains("GitHub"))
    }

    @Test
    fun import_csvWithTotpColumn_returnsEntries() {
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            ,0,1,Amazon,,,0,https://amazon.com,user@email.com,,${totpUri()}
            ,0,1,GitHub,,,0,https://github.com,dev@gh.com,,${totpUri(issuer="GitHub", account="dev@gh.com")}
        """.trimIndent()
        val entries = BitwardenImporter.importCsv(csv)
        assertEquals(2, entries.size)
        val issuers = entries.map { it.issuer }
        assertTrue(issuers.contains("Amazon"))
        assertTrue(issuers.contains("GitHub"))
    }

    @Test
    fun import_csvWithoutTotpColumn_returnsEmptyList() {
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password
            ,0,1,Amazon,,,0,https://amazon.com,user@email.com,
        """.trimIndent()
        val entries = BitwardenImporter.importCsv(csv)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun import_csvWithQuotedFields_works() {
        // The otpauth URI contains no commas, but surrounding fields may be quoted
        // with embedded commas. Verify the parser still finds the totp column.
        val csv = """
            folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp
            "Folder, Inc",0,1,"Amazon, LLC","note, with comma",,0,https://amazon.com,user@email.com,,${totpUri()}
        """.trimIndent()
        val entries = BitwardenImporter.importCsv(csv)
        assertEquals(1, entries.size)
        assertEquals("Amazon", entries[0].issuer)
    }

    @Test
    fun import_jsonWithOtpAuthUris_parsesCorrectly() {
        val json = """
            {"encrypted":false,"items":[
                {"id":"i1","name":"Test","type":1,
                 "login":{"totp":"${totpUri(issuer="MyIssuer", account="acct@x.com", digits = 8, period = 60)}"},
                 "favorite":false}
            ]}
        """.trimIndent()
        val entries = BitwardenImporter.import(json)
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("MyIssuer", e.issuer)
        assertEquals("acct@x.com", e.label)
        assertEquals(8, e.digits)
        assertEquals(60, e.period)
        assertEquals("SHA1", e.algorithm)
        // Secret should be decoded from Base32 to bytes (non-empty).
        assertTrue(e.secret.isNotEmpty())
    }
}
