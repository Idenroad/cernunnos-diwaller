package com.cernunnos.authenticator.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.cernunnos.authenticator.data.model.DocumentEntry
import com.cernunnos.authenticator.data.model.DocumentType
import com.cernunnos.authenticator.data.storage.DocumentStore
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Repository for the Documents vault.
 *
 * Handles:
 * - Photo compression (target ~500KB per image)
 * - Thumbnail generation (~5KB base64 for grid view)
 * - CRUD delegation to [DocumentStore]
 * - Expiration checking
 */
class DocumentRepository(private val store: DocumentStore) {

    val isInitialized: Boolean get() = store.isInitialized
    val isUnlocked: Boolean get() = store.isUnlocked

    fun initialize(passphrase: CharArray) = store.initialize(passphrase)
    fun unlock(passphrase: CharArray) = store.unlock(passphrase)
    fun lock() = store.lock()

    /**
     * Add a document from an image input stream (camera or gallery).
     * Compresses the image to ~500KB and generates a thumbnail.
     */
    fun addDocument(
        imageStream: InputStream,
        type: DocumentType,
        title: String,
        expirationDate: Long? = null,
        notes: String = "",
    ): DocumentEntry {
        // Read the entire stream into a ByteArray first, so we can create
        // two independent ByteArrayInputStreams: one for BitmapFactory and
        // one for ExifInterface. This fixes the bug where decodeStream
        // consumed the stream, leaving nothing for EXIF reading.
        val rawBytes = imageStream.use { it.readBytes() }
        val rawBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            ?: error("Failed to decode image")
        val oriented = applyExifOrientation(rawBitmap, java.io.ByteArrayInputStream(rawBytes))
        // Generate thumbnail BEFORE compressBitmap, because compressBitmap may
        // recycle the oriented bitmap (use-after-recycle bug).
        val thumbnail = generateThumbnail(oriented)
        val compressed = compressBitmap(oriented, TARGET_SIZE_BYTES)
        val thumbnailB64 = bitmapToBase64(thumbnail)

        val id = java.util.UUID.randomUUID().toString()
        val entry = DocumentEntry(
            id = id,
            type = type,
            title = title,
            encryptedFileName = "", // assigned by store
            thumbnailBase64 = thumbnailB64,
            expirationDate = expirationDate,
            notes = notes,
        )
        return store.addDocument(compressed, null, entry)
    }

    /**
     * Add a document from a Bitmap (e.g. from camera intent).
     *
     * NOTE: This function does NOT recycle the input bitmaps. The caller
     * retains ownership and is responsible for recycling them.
     *
     * @param rectoBitmap The front side of the document.
     * @param versoBitmap The back side, or null for recto-only documents.
     */
    fun addDocument(
        rectoBitmap: Bitmap,
        versoBitmap: Bitmap?,
        type: DocumentType,
        title: String,
        expirationDate: Long? = null,
        notes: String = "",
    ): DocumentEntry {
        // Compress and generate thumbnail from copies to avoid recycling
        // the caller's bitmaps. compressBitmap and generateThumbnail may
        // recycle their input bitmap, so we pass copies.
        val rectoCopy = Bitmap.createBitmap(rectoBitmap)
        val versoCopy = versoBitmap?.let { Bitmap.createBitmap(it) }
        val thumbnailCopy = Bitmap.createBitmap(rectoBitmap)

        val compressedRecto = compressBitmap(rectoCopy, TARGET_SIZE_BYTES)
        val compressedVerso = versoCopy?.let { compressBitmap(it, TARGET_SIZE_BYTES) }
        val thumbnail = generateThumbnail(thumbnailCopy)
        val thumbnailB64 = bitmapToBase64(thumbnail)

        val id = java.util.UUID.randomUUID().toString()
        val entry = DocumentEntry(
            id = id,
            type = type,
            title = title,
            encryptedFileName = "",
            thumbnailBase64 = thumbnailB64,
            expirationDate = expirationDate,
            notes = notes,
        )
        return store.addDocument(compressedRecto, compressedVerso, entry)
    }

    fun updateDocument(entry: DocumentEntry) = store.updateDocument(entry)
    fun deleteDocument(id: String) = store.deleteDocument(id)
    fun getDocuments(): List<DocumentEntry> = store.getDocuments()
    fun getDocumentImage(entry: DocumentEntry): ByteArray = store.getDocumentImage(entry)
    fun getDocumentVersoImage(entry: DocumentEntry): ByteArray? = store.getDocumentVersoImage(entry)

    fun exportDocument(id: String, passphrase: String): ByteArray? = store.exportDocument(id, passphrase)
    fun importDocument(data: ByteArray, passphrase: String, title: String, type: DocumentType): DocumentEntry? =
        store.importDocument(data, passphrase, title, type)

    /**
     * Returns documents that expire within the given number of days.
     */
    fun getExpiringDocuments(daysAhead: Int = 30): List<DocumentEntry> {
        val now = System.currentTimeMillis()
        val threshold = now + daysAhead * 24L * 60L * 60L * 1000L
        return store.getDocuments().filter { entry ->
            entry.expirationDate != null && entry.expirationDate <= threshold && entry.expirationDate >= now
        }
    }

    /**
     * Returns documents that have already expired.
     */
    fun getExpiredDocuments(): List<DocumentEntry> {
        val now = System.currentTimeMillis()
        return store.getDocuments().filter { it.expirationDate != null && it.expirationDate < now }
    }

    // ── Image processing ──

    private fun compressBitmap(bitmap: Bitmap, targetBytes: Int): ByteArray {
        // Use WEBP for better quality/size ratio than JPEG.
        // WEBP_LOSSY at quality 90 gives near-lossless quality for documents.
        val format = Bitmap.CompressFormat.WEBP
        var quality = 90
        var bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
        }
        // Iteratively reduce quality until under target, but don't go below 70
        // (documents need to remain readable — text, numbers, photos)
        while (bytes.size > targetBytes && quality > 70) {
            quality -= 5
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(format, quality, out)
                out.toByteArray()
            }
        }
        // If still too large, scale down — but keep minimum 1000px for readability
        if (bytes.size > targetBytes) {
            val scale = Math.sqrt(targetBytes.toDouble() / bytes.size)
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1000)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1000)
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            if (scaled != bitmap) bitmap.recycle()
            bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(format, quality, out)
                out.toByteArray()
            }
        }
        return bytes
    }

    private fun generateThumbnail(bitmap: Bitmap): Bitmap {
        // Larger thumbnail (400px) for better grid display
        val maxDim = 400
        val scale = if (bitmap.width > bitmap.height) {
            maxDim.toFloat() / bitmap.width
        } else {
            maxDim.toFloat() / bitmap.height
        }
        return if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            // Recycle the original bitmap only when a new scaled bitmap was created.
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } else {
            bitmap
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Higher quality thumbnail (90 instead of 70)
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun applyExifOrientation(bitmap: Bitmap, stream: InputStream): Bitmap {
        return try {
            // Mark support is required for reset. ByteArrayInputStream supports
            // mark/reset natively. For other streams, mark may not work.
            if (!stream.markSupported()) return bitmap
            stream.mark(stream.available())
            val exif = ExifInterface(stream)
            stream.reset()
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: Exception) {
            bitmap
        }
    }

    companion object {
        private const val TARGET_SIZE_BYTES = 4 * 1024 * 1024 // 4MB — documents need high quality for readability
    }
}
