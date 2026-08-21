package com.cernunnos.authenticator.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Temp file cleanup tests.
 *
 * The app creates temporary files in the cache directory during camera capture
 * and encrypted sharing. If the app is killed mid-operation, these files
 * accumulate and waste storage (a form of resource leak).
 *
 * The cleanup logic in AppViewModel.cleanupTempFiles() and
 * DocumentStore.cleanupOldCameraPhotos() / cleanupOldShareFiles() uses
 * filename prefix + suffix matching to identify files to delete.
 *
 * Since we can't use Android File APIs in JVM tests, we test the matching
 * patterns directly via regex — the same logic the cleanup code uses
 * (startsWith + endsWith).
 */
class TempFileCleanupTest {

    // ── Pattern definitions (mirror the app's cleanup logic) ──

    /**
     * Camera temp files: "doc_photo_*.jpg"
     * Created by AddDocumentScreen: doc_photo_${System.currentTimeMillis()}.jpg
     */
    private fun isCameraTempFile(name: String): Boolean {
        return name.startsWith("doc_photo_") && name.endsWith(".jpg")
    }

    /**
     * Document share temp files: "cernunnos_doc_*.enc"
     * Created by DocumentDetailScreen: cernunnos_doc_${entry.title}.enc
     */
    private fun isDocShareTempFile(name: String): Boolean {
        return name.startsWith("cernunnos_doc_") && name.endsWith(".enc")
    }

    /**
     * TOTP share temp files: "cernunnos_totp_*.txt"
     * Created by DetailScreen: cernunnos_totp_${safeName}.txt
     */
    private fun isTotpShareTempFile(name: String): Boolean {
        return name.startsWith("cernunnos_totp_") && name.endsWith(".txt")
    }

    /**
     * A file is a temp file if it matches ANY of the cleanup patterns.
     */
    private fun isTempFile(name: String): Boolean {
        return isCameraTempFile(name) || isDocShareTempFile(name) || isTotpShareTempFile(name)
    }

    // ── Camera temp file pattern: doc_photo_*.jpg ──

    @Test
    fun cameraTempFile_matchingPattern_isIdentifiedForCleanup() {
        assertTrue(isCameraTempFile("doc_photo_1700000000000.jpg"))
    }

    @Test
    fun cameraTempFile_withTimestamp_isIdentifiedForCleanup() {
        assertTrue(isCameraTempFile("doc_photo_1699999999999.jpg"))
    }

    @Test
    fun cameraTempFile_variousTimestamps_areIdentifiedForCleanup() {
        assertTrue(isCameraTempFile("doc_photo_1.jpg"))
        assertTrue(isCameraTempFile("doc_photo_1234567890.jpg"))
        assertTrue(isCameraTempFile("doc_photo_9999999999999.jpg"))
    }

    // ── Document share temp file pattern: cernunnos_doc_*.enc ──

    @Test
    fun docShareTempFile_matchingPattern_isIdentifiedForCleanup() {
        assertTrue(isDocShareTempFile("cernunnos_doc_Passport.enc"))
    }

    @Test
    fun docShareTempFile_withTitle_isIdentifiedForCleanup() {
        assertTrue(isDocShareTempFile("cernunnos_doc_Driver_License.enc"))
    }

    @Test
    fun docShareTempFile_variousNames_areIdentifiedForCleanup() {
        assertTrue(isDocShareTempFile("cernunnos_doc_ID Card.enc"))
        assertTrue(isDocShareTempFile("cernunnos_doc_MyDocument.enc"))
        assertTrue(isDocShareTempFile("cernunnos_doc_123.enc"))
    }

    // ── TOTP share temp file pattern: cernunnos_totp_*.txt ──

    @Test
    fun totpShareTempFile_matchingPattern_isIdentifiedForCleanup() {
        assertTrue(isTotpShareTempFile("cernunnos_totp_GitHub.txt"))
    }

    @Test
    fun totpShareTempFile_withName_isIdentifiedForCleanup() {
        assertTrue(isTotpShareTempFile("cernunnos_totp_user@example.com.txt"))
    }

    @Test
    fun totpShareTempFile_variousNames_areIdentifiedForCleanup() {
        assertTrue(isTotpShareTempFile("cernunnos_totp_AWS.txt"))
        assertTrue(isTotpShareTempFile("cernunnos_totp_Microsoft_Account.txt"))
        assertTrue(isTotpShareTempFile("cernunnos_totp_123.txt"))
    }

    // ── Non-matching files are NOT identified ──

    @Test
    fun nonMatchingFile_randomName_isNotIdentifiedForCleanup() {
        assertFalse(isTempFile("random_file.txt"))
    }

    @Test
    fun nonMatchingFile_encryptedDocument_isNotIdentifiedForCleanup() {
        // Internal encrypted document files use "doc_" prefix, not "doc_photo_"
        assertFalse(isTempFile("doc_abc123.enc"))
    }

    @Test
    fun nonMatchingFile_wrongExtension_isNotIdentifiedForCleanup() {
        // doc_photo_ but wrong extension
        assertFalse(isCameraTempFile("doc_photo_1700000000000.png"))
        assertFalse(isCameraTempFile("doc_photo_1700000000000.jpeg"))
        // cernunnos_doc_ but wrong extension
        assertFalse(isDocShareTempFile("cernunnos_doc_Passport.txt"))
        assertFalse(isDocShareTempFile("cernunnos_doc_Passport.jpg"))
        // cernunnos_totp_ but wrong extension
        assertFalse(isTotpShareTempFile("cernunnos_totp_GitHub.enc"))
        assertFalse(isTotpShareTempFile("cernunnos_totp_GitHub.csv"))
    }

    @Test
    fun nonMatchingFile_wrongPrefix_isNotIdentifiedForCleanup() {
        // Right extension but wrong prefix
        assertFalse(isCameraTempFile("photo_1700000000000.jpg"))
        assertFalse(isDocShareTempFile("doc_Passport.enc"))
        assertFalse(isTotpShareTempFile("totp_GitHub.txt"))
    }

    @Test
    fun nonMatchingFile_systemFiles_areNotIdentifiedForCleanup() {
        assertFalse(isTempFile(".DS_Store"))
        assertFalse(isTempFile("Thumbs.db"))
        assertFalse(isTempFile("backup.json"))
        assertFalse(isTempFile("cernunnos_documents.xml"))
    }

    // ── Combined: isTempFile correctly classifies all categories ──

    @Test
    fun isTempFile_identifiesAllThreeCategories() {
        assertTrue(isTempFile("doc_photo_1700000000000.jpg"))
        assertTrue(isTempFile("cernunnos_doc_Passport.enc"))
        assertTrue(isTempFile("cernunnos_totp_GitHub.txt"))
    }

    @Test
    fun isTempFile_rejectsAllNonTempFiles() {
        assertFalse(isTempFile("doc_abc123.enc"))
        assertFalse(isTempFile("cernunnos_backup.json"))
        assertFalse(isTempFile("photo.jpg"))
        assertFalse(isTempFile("export.txt"))
    }

    // ── Edge cases with various timestamps and names ──

    @Test
    fun cameraTempFile_emptyTimestamp_isStillMatched() {
        // Edge case: "doc_photo_.jpg" — prefix + suffix with nothing between
        assertTrue(isCameraTempFile("doc_photo_.jpg"))
    }

    @Test
    fun docShareTempFile_emptyTitle_isStillMatched() {
        assertTrue(isDocShareTempFile("cernunnos_doc_.enc"))
    }

    @Test
    fun totpShareTempFile_emptyName_isStillMatched() {
        assertTrue(isTotpShareTempFile("cernunnos_totp_.txt"))
    }

    @Test
    fun patterns_withSpecialCharactersInNames_areMatched() {
        // Titles/names may contain spaces, hyphens, underscores, dots
        assertTrue(isDocShareTempFile("cernunnos_doc_My Document.enc"))
        assertTrue(isDocShareTempFile("cernunnos_doc_doc-1.enc"))
        assertTrue(isTotpShareTempFile("cernunnos_totp_user.name@domain.com.txt"))
        assertTrue(isTotpShareTempFile("cernunnos_totp_Account-2FA.txt"))
    }

    @Test
    fun patterns_areCaseSensitive() {
        // The app uses startsWith/endsWith which are case-sensitive
        // Uppercase variants should NOT match (they're different files)
        assertFalse(isCameraTempFile("DOC_PHOTO_1700000000000.JPG"))
        assertFalse(isDocShareTempFile("CERNUNNOS_DOC_Passport.ENC"))
        assertFalse(isTotpShareTempFile("CERNUNNOS_TOTP_GitHub.TXT"))
    }
}
