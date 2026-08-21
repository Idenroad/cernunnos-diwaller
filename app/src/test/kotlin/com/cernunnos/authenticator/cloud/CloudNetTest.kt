package com.cernunnos.authenticator.cloud

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for CloudNet retry logic (M3 fix).
 */
class CloudNetTest {

    @Test
    fun retry_succeedsOnFirstAttempt_returnsImmediately() {
        var calls = 0
        val result = CloudNet.retry("Test", "op1", maxRetries = 3) {
            calls++
            "success"
        }
        assertEquals("success", result)
        assertEquals(1, calls)
    }

    @Test
    fun retry_succeedsOnSecondAttempt_retriesOnce() {
        var calls = 0
        val result = CloudNet.retry("Test", "op2", maxRetries = 3) { attempt ->
            calls++
            if (attempt == 0) null else "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(2, calls)
    }

    @Test
    fun retry_allAttemptsFail_returnsNull() {
        var calls = 0
        val result = CloudNet.retry("Test", "op3", maxRetries = 2) {
            calls++
            null
        }
        assertNull(result)
        assertEquals(2, calls)
    }

    @Test
    fun retry_exceptionThenSuccess_returnsResult() {
        var calls = 0
        val result = CloudNet.retry("Test", "op4", maxRetries = 3) { attempt ->
            calls++
            if (attempt == 0) throw java.io.IOException("network down")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun retry_allAttemptsThrow_returnsNull() {
        var calls = 0
        val result = CloudNet.retry<String>("Test", "op5", maxRetries = 2) {
            calls++
            throw java.io.IOException("permanent failure")
        }
        assertNull(result)
        assertEquals(2, calls)
    }

    @Test
    fun retry_singleAttempt_noRetry() {
        var calls = 0
        val result = CloudNet.retry("Test", "op6", maxRetries = 1) {
            calls++
            null
        }
        assertNull(result)
        assertEquals(1, calls)
    }
}
