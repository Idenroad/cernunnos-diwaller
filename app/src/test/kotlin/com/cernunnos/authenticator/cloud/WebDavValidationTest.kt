package com.cernunnos.authenticator.cloud

import org.junit.Test
import org.junit.Assert.*

/**
 * Pure-JVM tests for [WebDavValidator] (URL normalization + auth-error detection).
 */
class WebDavValidationTest {

    // --- validateUrl ---

    @Test
    fun validateUrl_httpsUrl_returnsNormalized() {
        assertEquals("https://example.com/dav/",
            WebDavValidator.validateUrl("https://example.com/dav"))
    }

    @Test
    fun validateUrl_httpUrl_returnsNormalized() {
        assertEquals("http://example.com/dav/",
            WebDavValidator.validateUrl("http://example.com/dav"))
    }

    @Test
    fun validateUrl_withTrailingSlash_returnsAsIs() {
        assertEquals("https://example.com/dav/",
            WebDavValidator.validateUrl("https://example.com/dav/"))
    }

    @Test
    fun validateUrl_withoutTrailingSlash_addsSlash() {
        assertEquals("https://example.com/",
            WebDavValidator.validateUrl("https://example.com"))
    }

    @Test
    fun validateUrl_ftpScheme_throws() {
        try {
            WebDavValidator.validateUrl("ftp://example.com/dav")
            fail("Expected IllegalArgumentException for ftp scheme")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("http://") || e.message!!.contains("https://"))
        }
    }

    @Test
    fun validateUrl_emptyString_throws() {
        try {
            WebDavValidator.validateUrl("")
            fail("Expected IllegalArgumentException for empty URL")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun validateUrl_fileScheme_throws() {
        try {
            WebDavValidator.validateUrl("file:///path/to/dav")
            fail("Expected IllegalArgumentException for file scheme")
        } catch (e: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun validateUrl_withWhitespace_trimsAndValidates() {
        assertEquals("https://example.com/dav/",
            WebDavValidator.validateUrl("  https://example.com/dav  "))
    }

    // --- isAuthenticationError ---

    @Test
    fun isAuthenticationError_401_returnsTrue() {
        assertTrue(WebDavValidator.isAuthenticationError(401))
    }

    @Test
    fun isAuthenticationError_200_returnsFalse() {
        assertFalse(WebDavValidator.isAuthenticationError(200))
    }

    @Test
    fun isAuthenticationError_403_returnsFalse() {
        assertFalse(WebDavValidator.isAuthenticationError(403))
    }

    @Test
    fun isAuthenticationError_500_returnsFalse() {
        assertFalse(WebDavValidator.isAuthenticationError(500))
    }

    // --- isInsecureUrl ---

    @Test
    fun isInsecureUrl_http_returnsTrue() {
        assertTrue(WebDavValidator.isInsecureUrl("http://example.com/dav/"))
    }

    @Test
    fun isInsecureUrl_https_returnsFalse() {
        assertFalse(WebDavValidator.isInsecureUrl("https://example.com/dav/"))
    }

    // --- isPrivateLanUrl ---

    @Test
    fun isPrivateLanUrl_localhost_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://localhost:8080/dav/"))
    }

    @Test
    fun isPrivateLanUrl_127_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://127.0.0.1:8080/dav/"))
    }

    @Test
    fun isPrivateLanUrl_10_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://10.0.0.5/dav/"))
    }

    @Test
    fun isPrivateLanUrl_192168_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://192.168.1.100/dav/"))
    }

    @Test
    fun isPrivateLanUrl_17216_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://172.16.0.1/dav/"))
    }

    @Test
    fun isPrivateLanUrl_17231_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://172.31.255.255/dav/"))
    }

    @Test
    fun isPrivateLanUrl_17215_returnsFalse() {
        assertFalse(WebDavValidator.isPrivateLanUrl("http://172.15.0.1/dav/"))
    }

    @Test
    fun isPrivateLanUrl_17232_returnsFalse() {
        assertFalse(WebDavValidator.isPrivateLanUrl("http://172.32.0.1/dav/"))
    }

    @Test
    fun isPrivateLanUrl_localDomain_returnsTrue() {
        assertTrue(WebDavValidator.isPrivateLanUrl("http://nas.local/dav/"))
    }

    @Test
    fun isPrivateLanUrl_publicIp_returnsFalse() {
        assertFalse(WebDavValidator.isPrivateLanUrl("http://8.8.8.8/dav/"))
    }

    @Test
    fun isPrivateLanUrl_publicDomain_returnsFalse() {
        assertFalse(WebDavValidator.isPrivateLanUrl("http://example.com/dav/"))
    }

    // --- isCleartextSafe ---

    @Test
    fun isCleartextSafe_https_returnsTrue() {
        assertTrue(WebDavValidator.isCleartextSafe("https://example.com/dav/"))
    }

    @Test
    fun isCleartextSafe_httpLan_returnsTrue() {
        assertTrue(WebDavValidator.isCleartextSafe("http://192.168.1.100/dav/"))
    }

    @Test
    fun isCleartextSafe_httpPublic_returnsFalse() {
        assertFalse(WebDavValidator.isCleartextSafe("http://example.com/dav/"))
    }

    @Test
    fun isCleartextSafe_httpPublicIp_returnsFalse() {
        assertFalse(WebDavValidator.isCleartextSafe("http://8.8.8.8/dav/"))
    }
}
