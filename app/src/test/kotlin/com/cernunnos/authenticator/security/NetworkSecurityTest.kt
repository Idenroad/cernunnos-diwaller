package com.cernunnos.authenticator.security

import com.cernunnos.authenticator.cloud.WebDavValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for network-security related validation logic.
 *
 * These tests verify that [WebDavValidator] correctly classifies URLs as
 * insecure (http://) vs secure (https://) and that URL validation accepts
 * only HTTP(S) schemes while rejecting empty strings, missing schemes, and
 * non-HTTP schemes such as ftp://.
 */
class NetworkSecurityTest {

    @Test
    fun isInsecureUrl_returnsTrueForHttp() {
        assertTrue(WebDavValidator.isInsecureUrl("http://example.com/dav"))
    }

    @Test
    fun isInsecureUrl_returnsFalseForHttps() {
        assertFalse(WebDavValidator.isInsecureUrl("https://example.com/dav"))
    }

    @Test
    fun isInsecureUrl_returnsFalseForEmptyString() {
        assertFalse(WebDavValidator.isInsecureUrl(""))
    }

    @Test
    fun validateUrl_acceptsHttps() {
        val result = WebDavValidator.validateUrl("https://example.com/dav")
        assertEquals("https://example.com/dav/", result)
    }

    @Test
    fun validateUrl_acceptsHttpForLan() {
        val result = WebDavValidator.validateUrl("http://192.168.1.10/dav")
        assertEquals("http://192.168.1.10/dav/", result)
    }

    @Test
    fun validateUrl_rejectsFtp() {
        try {
            WebDavValidator.validateUrl("ftp://example.com/dav")
            org.junit.Assert.fail("Expected IllegalArgumentException for ftp:// URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("http://") || e.message!!.contains("https://"))
        }
    }

    @Test
    fun validateUrl_rejectsEmptyString() {
        try {
            WebDavValidator.validateUrl("")
            org.junit.Assert.fail("Expected IllegalArgumentException for empty URL")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun validateUrl_rejectsUrlWithoutScheme() {
        try {
            WebDavValidator.validateUrl("example.com/dav")
            org.junit.Assert.fail("Expected IllegalArgumentException for URL without scheme")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("http://") || e.message!!.contains("https://"))
        }
    }
}
