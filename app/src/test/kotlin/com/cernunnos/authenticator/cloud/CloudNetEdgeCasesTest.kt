package com.cernunnos.authenticator.cloud

import org.junit.Test
import org.junit.Assert.*

/**
 * Additional edge-case tests for [CloudNet.retry] (complements [CloudNetTest]).
 */
class CloudNetEdgeCasesTest {

    @Test
    fun retry_maxRetriesZero_returnsNullImmediately() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-zero", maxRetries = 0) {
            calls++
            "should-not-run"
        }
        assertNull(result)
        assertEquals(0, calls)
    }

    @Test
    fun retry_maxRetriesNegative_returnsNullImmediately() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-negative", maxRetries = -1) {
            calls++
            "should-not-run"
        }
        assertNull(result)
        assertEquals(0, calls)
    }

    @Test
    fun retry_nullResult_returnsNull() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-null", maxRetries = 1) {
            calls++
            null
        }
        assertNull(result)
        assertEquals(1, calls)
    }

    @Test
    fun retry_nonNullResult_returnsImmediately() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-nonnull", maxRetries = 3) {
            calls++
            "done"
        }
        assertEquals("done", result)
        assertEquals(1, calls)
    }

    @Test
    fun retry_largeMaxRetries_stopsOnSuccess() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-large", maxRetries = 100) {
            calls++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun retry_exceptionOnLastAttempt_returnsNull() {
        var calls = 0
        val result = CloudNet.retry<String>("Test", "edge-last-throw", maxRetries = 2) { attempt ->
            calls++
            if (attempt == 1) throw java.io.IOException("boom") else null
        }
        assertNull(result)
        assertEquals(2, calls)
    }

    @Test
    fun retry_multipleExceptionsThenSuccess_returnsResult() {
        var calls = 0
        val result = CloudNet.retry("Test", "edge-multi-exc", maxRetries = 3) { attempt ->
            calls++
            if (attempt < 2) throw java.io.IOException("fail $attempt") else "recovered"
        }
        assertEquals("recovered", result)
        assertEquals(3, calls)
    }

    @Test
    fun retry_blockReceivesAttemptIndex() {
        val seen = mutableListOf<Int>()
        CloudNet.retry("Test", "edge-index", maxRetries = 3) { attempt ->
            seen.add(attempt)
            if (attempt < 2) null else "ok"
        }
        assertEquals(listOf(0, 1, 2), seen)
    }
}
