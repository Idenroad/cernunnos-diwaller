package com.cernunnos.authenticator.data.model

import kotlinx.serialization.Serializable

/**
 * Type of document stored in the Documents vault.
 */
enum class DocumentType(val key: String) {
    DRIVER_LICENSE("driver_license"),
    PASSPORT("passport"),
    ID_CARD("id_card"),
    INSURANCE("insurance"),
    VEHICLE_REGISTRATION("vehicle_registration"),
    TAX_DOCUMENT("tax_document"),
    MEDICAL("medical"),
    OTHER("other");

    companion object {
        fun fromKey(key: String): DocumentType =
            entries.find { it.key == key } ?: OTHER
    }
}

/**
 * A document entry in the Documents vault.
 *
 * The actual photo/image is stored as an encrypted file on disk (path = encryptedFileName).
 * This metadata record is stored in the encrypted document index.
 *
 * @param id Unique identifier (UUID).
 * @param type Type of document (permit, passport, etc.).
 * @param title Human-readable title, e.g. "Permis de conduire".
 * @param encryptedFileName Name of the encrypted recto file in the documents directory.
 * @param encryptedVersoFileName Name of the encrypted verso file, or null if recto only.
 * @param thumbnailBase64 Small base64-encoded thumbnail for the grid view (compressed, ~5KB).
 * @param hasVerso Whether this document has a verso (back side).
 * @param expirationDate Epoch millis of document expiration, or null if no expiration.
 * @param notes Free-form notes (e.g. "Renouveler avant mars 2027").
 * @param createdAt Epoch millis of creation.
 * @param updatedAt Epoch millis of last update.
 */
@Serializable
data class DocumentEntry(
    val id: String,
    val type: DocumentType = DocumentType.OTHER,
    val title: String,
    val encryptedFileName: String,
    val encryptedVersoFileName: String? = null,
    val thumbnailBase64: String? = null,
    val hasVerso: Boolean = false,
    val expirationDate: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
