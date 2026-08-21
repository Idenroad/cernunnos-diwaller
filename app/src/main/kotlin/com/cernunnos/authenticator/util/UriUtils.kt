package com.cernunnos.authenticator.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Shared URI helpers to avoid duplication across screens and builders.
 */
object UriUtils {

    /**
     * Resolve the display name of a content URI via the ContentResolver,
     * falling back to the last path segment or "document" if unavailable.
     */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "document"
    }
}
