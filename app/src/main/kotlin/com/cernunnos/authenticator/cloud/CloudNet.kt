package com.cernunnos.authenticator.cloud

import android.util.Log
import kotlin.math.pow

/**
 * Network utilities for cloud providers:
 * - Retry with exponential backoff
 * - Consistent error logging
 */
object CloudNet {

    /**
     * Run [block] with up to [maxRetries] attempts.
     * Backoff: 1s, 2s, 4s... between attempts.
     * Returns the result of the last successful attempt, or null if all attempts fail.
     */
    fun <T> retry(
        tag: String,
        operation: String,
        maxRetries: Int = 3,
        block: (attempt: Int) -> T?,
    ): T? {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                val result = block(attempt)
                if (result != null) return result
                // Treat null as a soft failure; retry
            } catch (e: Exception) {
                lastError = e
                Log.w(tag, "$operation failed (attempt ${attempt + 1}/$maxRetries): ${e.message}")
            }
            if (attempt < maxRetries - 1) {
                try {
                    val delayMs = (2.0.pow(attempt.toDouble()) * 1000).toLong()
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        Log.e(tag, "$operation permanently failed after $maxRetries attempts", lastError)
        return null
    }

    /**
     * Log an error from a cloud operation.
     */
    fun logError(tag: String, operation: String, e: Exception) {
        Log.e(tag, "$operation failed: ${e.message}", e)
    }
}
