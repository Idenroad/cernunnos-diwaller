package com.cernunnos.authenticator.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cernunnos.authenticator.data.crypto.Argon2id
import com.cernunnos.authenticator.data.crypto.CryptoManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64 as JBase64

/**
 * Secure document envelope builder.
 *
 * Two output formats:
 * 1. PDF encrypted with password (AES-256, standard PDF encryption) — opens everywhere
 * 2. .cern file (AES-256-GCM + Argon2id) — opens only with Cernunnos Diwaller
 *
 * Supported input formats:
 * - PDF: included as pages directly
 * - JPG/JPEG, WEBP, PNG: converted to PDF pages
 * - MD, CSV, TXT: converted to text PDF pages
 */
object SecureDocumentBuilder {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private const val CERN_VERSION = "cern-v1"
    private const val CERN_DOC_TYPE = "documents"

    @Serializable
    private data class CernDocument(
        val fileName: String,
        val mimeType: String,
        val data: String, // base64
    )

    @Serializable
    private data class CernEnvelope(
        val version: String,
        val type: String,
        val documents: List<CernDocument>,
    )

    data class SecuredFile(
        val file: File,
        val mimeType: String,
        val fileName: String,
    )

    /**
     * Generate a random 16-character password.
     * Uses a mix of uppercase, lowercase, digits, and a few special chars.
     */
    fun generatePassword(length: Int = 16): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#\$%&*"
        val random = SecureRandom()
        return (1..length).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Build an encrypted PDF from a list of document URIs.
     *
     * @param context Android context
     * @param inputUris list of content URIs for input documents
     * @param password the password to encrypt the PDF with
     * @param outputDir directory to write the output file
     * @return the encrypted PDF file
     */
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50 MB per file
    private const val MAX_TOTAL_SIZE = 100 * 1024 * 1024L // 100 MB total
    private const val MAX_CERN_FILE_SIZE = 20 * 1024 * 1024L // 20 MB for .cern decrypt

    fun buildEncryptedPdf(
        context: Context,
        inputUris: List<Uri>,
        password: String,
        outputDir: File,
    ): SecuredFile {
        PDFBoxResourceLoader.init(context)

        val pdfDoc = PDDocument()
        try {
            var totalSize = 0L
            for (uri in inputUris) {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = UriUtils.getFileName(context, uri)

                // Check file size before loading
                val fileSize = getFileSize(context, uri)
                if (fileSize > MAX_FILE_SIZE) {
                    addTextPage(pdfDoc, "[File too large (>${MAX_FILE_SIZE / 1024 / 1024}MB): $fileName]", "Skipped")
                    continue
                }
                totalSize += fileSize
                if (totalSize > MAX_TOTAL_SIZE) {
                    addTextPage(pdfDoc, "[Total size limit reached]", "Limit")
                    break
                }

                try {
                    when {
                        mimeType == "application/pdf" -> {
                            // Load existing PDF and import its pages
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val bytes = IOUtils.readBounded(input, MAX_FILE_SIZE)
                                val sourceDoc = PDDocument.load(bytes)
                                for (page in sourceDoc.pages) {
                                    pdfDoc.addPage(page)
                                }
                                sourceDoc.close()
                            }
                        }
                        mimeType.startsWith("image/") -> {
                            // Convert image to PDF page
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val bytes = IOUtils.readBounded(input, MAX_FILE_SIZE)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    addImagePage(pdfDoc, bitmap)
                                    bitmap.recycle()
                                }
                            }
                        }
                        mimeType == "text/markdown" || mimeType == "text/csv" ||
                            mimeType == "text/plain" || fileName.endsWith(".md", true) ||
                            fileName.endsWith(".csv", true) || fileName.endsWith(".txt", true) -> {
                            // Convert text to PDF page
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                val bytes = IOUtils.readBounded(input, MAX_FILE_SIZE)
                                val text = String(bytes, Charsets.UTF_8)
                                addTextPage(pdfDoc, text, fileName)
                            }
                        }
                        else -> {
                            // Unsupported format — skip with a note page
                            addTextPage(pdfDoc, "[Unsupported format: $fileName]", "Unsupported")
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    addTextPage(pdfDoc, "[Failed to load (OOM): $fileName]", "Error")
                } catch (e: Exception) {
                    addTextPage(pdfDoc, "[Failed to load: $fileName — ${e.message}]", "Error")
                }
            }

            if (pdfDoc.numberOfPages == 0) {
                error("No valid documents to include in the PDF")
            }

            // Set metadata
            val info = PDDocumentInformation()
            info.title = "Cernunnos Secure Document"
            info.producer = "Cernunnos Diwaller"
            pdfDoc.documentInformation = info

            // Encrypt with password
            val accessPermission = AccessPermission()
            accessPermission.setCanPrint(true)
            accessPermission.setCanModify(false)
            accessPermission.setCanExtractContent(false)
            accessPermission.setCanModifyAnnotations(false)

            val protectionPolicy = StandardProtectionPolicy(password, password, accessPermission)
            protectionPolicy.encryptionKeyLength = 256
            pdfDoc.protect(protectionPolicy)

            // Write to file — stream directly to avoid double-copy in memory.
            outputDir.mkdirs()
            val outputFile = File(outputDir, "cernunnos_secure_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outputFile).use { fos ->
                pdfDoc.save(fos)
            }

            return SecuredFile(outputFile, "application/pdf", outputFile.name)
        } finally {
            pdfDoc.close()
        }
    }

    /**
     * Build a .cern encrypted file from a list of document URIs.
     *
     * Format: cern-v1:sha256(payload):base64(salt):base64(iv):base64(ciphertext)
     * The ciphertext is AES-256-GCM encrypted JSON of the envelope.
     *
     * @param context Android context
     * @param inputUris list of content URIs for input documents
     * @param password the password to encrypt with
     * @param outputDir directory to write the output file
     * @return the encrypted .cern file
     */
    fun buildEncryptedCern(
        context: Context,
        inputUris: List<Uri>,
        password: String,
        outputDir: File,
    ): SecuredFile {
        val documents = mutableListOf<CernDocument>()

        for (uri in inputUris) {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val fileName = UriUtils.getFileName(context, uri)

            // Check file size
            val fileSize = getFileSize(context, uri)
            if (fileSize > MAX_FILE_SIZE) {
                continue // Skip oversized files
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = IOUtils.readBounded(input, MAX_FILE_SIZE)
                if (bytes.isNotEmpty()) {
                    documents.add(CernDocument(
                        fileName = fileName,
                        mimeType = mimeType,
                        data = JBase64.getEncoder().encodeToString(bytes),
                    ))
                }
            }
        }

        if (documents.isEmpty()) {
            error("No valid documents to include")
        }

        val envelope = CernEnvelope(
            version = CERN_VERSION,
            type = CERN_DOC_TYPE,
            documents = documents,
        )

        val jsonBytes = json.encodeToString(envelope).toByteArray()
        val salt = Argon2id.generateSalt()
        val passChars = password.toCharArray()
        try {
            val encrypted = CryptoManager.encrypt(jsonBytes, passChars, salt)

            val payload = "${JBase64.getEncoder().withoutPadding().encodeToString(salt)}:" +
                "${JBase64.getEncoder().withoutPadding().encodeToString(encrypted.iv)}:" +
                JBase64.getEncoder().withoutPadding().encodeToString(encrypted.ciphertext)
            val checksum = sha256Base64(payload)
            val content = "$CERN_VERSION:$checksum:$payload"

            outputDir.mkdirs()
            val outputFile = File(outputDir, "cernunnos_secure_${System.currentTimeMillis()}.cern")
            outputFile.writeText(content)

            return SecuredFile(outputFile, "application/x-cernunnos", outputFile.name)
        } finally {
            passChars.fill(0.toChar())
        }
    }

    /**
     * Decrypt a .cern file and return the list of embedded documents.
     *
     * @param content the full content of the .cern file
     * @param password the password to decrypt with
     * @return list of decoded documents (fileName, mimeType, data bytes)
     */
    data class DecryptedDocument(
        val fileName: String,
        val mimeType: String,
        val data: ByteArray,
    )

    fun decryptCern(content: String, password: String): List<DecryptedDocument> {
        val trimmed = content.trim()
        if (!trimmed.startsWith("$CERN_VERSION:")) {
            error("Not a valid Cernunnos document file")
        }
        if (trimmed.length > MAX_CERN_FILE_SIZE) {
            error("File too large to decrypt")
        }

        val parts = trimmed.split(":")
        require(parts.size >= 5) { "Invalid Cernunnos file format" }

        val checksum = parts[1]
        val payload = parts.subList(2, parts.size).joinToString(":")

        // Verify checksum
        val computedChecksum = sha256Base64(payload)
        require(computedChecksum == checksum) { "File checksum mismatch — file may be corrupted" }

        val payloadParts = payload.split(":")
        require(payloadParts.size == 3) { "Invalid payload format" }

        val salt = JBase64.getDecoder().decode(payloadParts[0])
        val iv = JBase64.getDecoder().decode(payloadParts[1])
        val ciphertext = JBase64.getDecoder().decode(payloadParts[2])

        val encrypted = CryptoManager.EncryptedData(salt, iv, ciphertext)
        val passChars = password.toCharArray()
        try {
            val decrypted = CryptoManager.decrypt(encrypted, passChars)

            val envelope = json.decodeFromString<CernEnvelope>(String(decrypted, Charsets.UTF_8))
            require(envelope.type == CERN_DOC_TYPE) { "Not a document envelope" }

            return envelope.documents.map { doc ->
                DecryptedDocument(
                    fileName = doc.fileName,
                    mimeType = doc.mimeType,
                    data = JBase64.getDecoder().decode(doc.data),
                )
            }
        } finally {
            passChars.fill(0.toChar())
        }
    }

    // ── Private helpers ──

    private fun addImagePage(doc: PDDocument, bitmap: Bitmap) {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val ximage = LosslessFactory.createFromImage(doc, bitmap)
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height

        // Scale image to fit page with margins
        val margin = 36f // 0.5 inch
        val maxWidth = pageWidth - 2 * margin
        val maxHeight = pageHeight - 2 * margin

        val imageWidth = ximage.width.toFloat()
        val imageHeight = ximage.height.toFloat()
        val scale = minOf(maxWidth / imageWidth, maxHeight / imageHeight, 1f)

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale
        val x = (pageWidth - scaledWidth) / 2
        val y = (pageHeight - scaledHeight) / 2

        PDPageContentStream(doc, page).use { contentStream ->
            contentStream.drawImage(ximage, x, y, scaledWidth, scaledHeight)
        }
    }

    private fun addTextPage(doc: PDDocument, text: String, title: String) {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)

        val margin = 36f
        val yStart = page.mediaBox.height - margin
        val yCurrent = yStart
        val lineHeight = 14f

        PDPageContentStream(doc, page).use { contentStream ->
            // Simple text rendering — PDFBox Android doesn't support all font features
            // We write line by line
            val lines = text.lines()
            var y = yStart
            for (line in lines.take(50)) { // Limit to 50 lines per page
                if (y < margin) break
                contentStream.beginText()
                contentStream.newLineAtOffset(margin, y)
                contentStream.showText(line.take(80)) // Limit line width
                contentStream.endText()
                y -= lineHeight
            }
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && it.moveToFirst()) {
                return it.getLong(sizeIndex)
            }
        }
        return 0L
    }

    private fun sha256Base64(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return JBase64.getEncoder().withoutPadding().encodeToString(digest.digest(data.toByteArray()))
    }
}
