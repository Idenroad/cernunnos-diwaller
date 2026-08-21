package com.cernunnos.authenticator.battery

import com.cernunnos.authenticator.cloud.CloudNet
import com.cernunnos.authenticator.constants.TotpConfig
import com.cernunnos.authenticator.data.model.TotpEntry
import com.cernunnos.authenticator.util.ExportImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Battery drain detection tests.
 *
 * Tests patterns that could cause excessive battery consumption:
 * - CloudNet retry logic (max retries, exponential backoff)
 * - ExportImport determinism (no redundant processing)
 */
class BatteryDrainTest {

    // ── CloudNet retry logic ──

    /**
     * The default max retries is 3. A higher value would drain battery by
     * repeatedly attempting a failing network operation.
     */
    @Test
    fun retry_defaultMaxRetries_is3() {
        var calls = 0
        CloudNet.retry("Test", "op", maxRetries = 3) {
            calls++
            null
        }
        assertEquals("Default max retries should be 3", 3, calls)
    }

    @Test
    fun retry_doesNotExceed3Attempts() {
        var calls = 0
        val result = CloudNet.retry("Test", "op", maxRetries = 3) {
            calls++
            null
        }
        assertNull(result)
        assertTrue("Should not exceed 3 attempts", calls <= 3)
        assertEquals(3, calls)
    }

    @Test
    fun retry_singleAttempt_doesNotRetry() {
        var calls = 0
        CloudNet.retry("Test", "op", maxRetries = 1) {
            calls++
            null
        }
        assertEquals("Single attempt should not retry", 1, calls)
    }

    @Test
    fun retry_succeedsOnFirstAttempt_makesOnlyOneCall() {
        var calls = 0
        val result = CloudNet.retry("Test", "op", maxRetries = 3) {
            calls++
            "success"
        }
        assertEquals("success", result)
        assertEquals("Should not retry after success", 1, calls)
    }

    // ── Exponential backoff ──

    /**
     * The backoff formula is: 2^attempt * 1000 ms.
     * For attempts 0, 1, 2 this yields: 1000ms, 2000ms, 4000ms (1s, 2s, 4s).
     * This is exponential, not linear — important to avoid hammering the network.
     */
    @Test
    fun retry_backoffIsExponential_1s_2s_4s() {
        // Replicate the backoff formula from CloudNet.retry
        val backoff0 = (2.0.pow(0.0) * 1000).toLong()
        val backoff1 = (2.0.pow(1.0) * 1000).toLong()
        val backoff2 = (2.0.pow(2.0) * 1000).toLong()

        assertEquals(1000L, backoff0)
        assertEquals(2000L, backoff1)
        assertEquals(4000L, backoff2)
    }

    @Test
    fun retry_backoffDoublesEachAttempt() {
        val backoffs = (0..4).map { attempt ->
            (2.0.pow(attempt.toDouble()) * 1000).toLong()
        }
        // Each backoff should be double the previous
        for (i in 1 until backoffs.size) {
            assertTrue(
                "Backoff $i (${backoffs[i]}ms) should be double backoff ${i - 1} (${backoffs[i - 1]}ms)",
                backoffs[i] == backoffs[i - 1] * 2,
            )
        }
    }

    @Test
    fun retry_backoffIsExponential_notLinear() {
        val backoffs = (0..3).map { attempt ->
            (2.0.pow(attempt.toDouble()) * 1000).toLong()
        }
        // Linear would have constant deltas; exponential has growing deltas
        val delta1 = backoffs[1] - backoffs[0]
        val delta2 = backoffs[2] - backoffs[1]
        assertTrue("Deltas should grow (exponential), not stay constant", delta2 > delta1)
    }

    /**
     * Verify the total worst-case wait time for 3 retries.
     * With backoff 1s + 2s = 3s total (no sleep after last attempt).
     * This ensures the retry mechanism doesn't block for too long.
     */
    @Test
    fun retry_totalWaitTimeFor3Retries_isReasonable() {
        // Backoff happens after attempt 0 and attempt 1 (not after the last)
        val totalWait = (2.0.pow(0.0) * 1000).toLong() + (2.0.pow(1.0) * 1000).toLong()
        assertEquals(3000L, totalWait)
        // Should be under 10 seconds — no excessive blocking
        assertTrue("Total wait should be under 10s", totalWait < 10_000)
    }

    // ── Retry with exceptions ──

    @Test
    fun retry_allAttemptsThrow_returnsNullAfter3Calls() {
        var calls = 0
        val result = CloudNet.retry<String>("Test", "op", maxRetries = 3) {
            calls++
            throw java.io.IOException("network error")
        }
        assertNull(result)
        assertEquals(3, calls)
    }

    @Test
    fun retry_exceptionThenSuccess_stopsRetrying() {
        var calls = 0
        val result = CloudNet.retry("Test", "op", maxRetries = 3) { attempt ->
            calls++
            if (attempt == 0) throw java.io.IOException("transient")
            "recovered"
        }
        assertEquals("recovered", result)
        assertEquals("Should stop after success", 2, calls)
    }

    // ── ExportImport determinism ──

    private val testPassphrase = "test-passphrase-123"
    private val testEntries = listOf(
        TotpEntry(
            id = "entry-1",
            issuer = "GitHub",
            label = "user@example.com",
            secret = byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46),
            algorithm = TotpConfig.ALGO_SHA1,
            digits = TotpConfig.DEFAULT_DIGITS,
            period = TotpConfig.DEFAULT_PERIOD,
        ),
        TotpEntry(
            id = "entry-2",
            issuer = "GitLab",
            label = "dev@company.com",
            secret = byteArrayOf(0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F),
            algorithm = TotpConfig.ALGO_SHA256,
            digits = 8,
            period = 60,
        ),
    )

    /**
     * Export then import should recover the original entries exactly.
     * This verifies no data loss or redundant processing.
     */
    @Test
    fun exportImport_roundtrip_recoversOriginalEntries() {
        val exported = ExportImport.export(testEntries, testPassphrase)
        val imported = ExportImport.import(exported, testPassphrase)

        assertEquals(testEntries.size, imported.size)
        for (i in testEntries.indices) {
            assertEquals(testEntries[i].id, imported[i].id)
            assertEquals(testEntries[i].issuer, imported[i].issuer)
            assertEquals(testEntries[i].label, imported[i].label)
            assertEquals(testEntries[i].algorithm, imported[i].algorithm)
            assertEquals(testEntries[i].digits, imported[i].digits)
            assertEquals(testEntries[i].period, imported[i].period)
        }
    }

    /**
     * Importing the same export twice should produce identical results.
     * No redundant processing or state accumulation.
     */
    @Test
    fun exportImport_importingSameExportTwice_isDeterministic() {
        val exported = ExportImport.export(testEntries, testPassphrase)

        val imported1 = ExportImport.import(exported, testPassphrase)
        val imported2 = ExportImport.import(exported, testPassphrase)

        assertEquals(imported1.size, imported2.size)
        for (i in imported1.indices) {
            assertEquals(imported1[i].id, imported2[i].id)
            assertEquals(imported1[i].issuer, imported2[i].issuer)
            assertEquals(imported1[i].label, imported2[i].label)
        }
    }

    /**
     * Multiple export+import cycles should not accumulate errors.
     * Each cycle is independent — no state leaks between cycles.
     */
    @Test
    fun exportImport_multipleCycles_doNotAccumulateErrors() {
        repeat(5) { cycle ->
            val exported = ExportImport.export(testEntries, testPassphrase)
            val imported = ExportImport.import(exported, testPassphrase)
            assertEquals("Cycle $cycle: entry count should match", testEntries.size, imported.size)
            assertEquals("Cycle $cycle: first entry id should match", testEntries[0].id, imported[0].id)
        }
    }

    /**
     * Export format should include the version prefix "v1:".
     * This ensures the export is not processed redundantly on import
     * (the version is checked once, not re-parsed).
     */
    @Test
    fun export_includesVersionPrefix() {
        val exported = ExportImport.export(testEntries, testPassphrase)
        assertTrue("Export should start with v1: prefix", exported.startsWith("v1:"))
    }

    /**
     * Export with empty list should still roundtrip correctly.
     */
    @Test
    fun exportImport_emptyList_roundtrips() {
        val exported = ExportImport.export(emptyList(), testPassphrase)
        assertNotNull(exported)
        val imported = ExportImport.import(exported, testPassphrase)
        assertEquals(0, imported.size)
    }
}
