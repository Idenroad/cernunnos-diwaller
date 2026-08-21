package com.cernunnos.authenticator.cloud

/**
 * Pure-JVM validation utilities for WebDAV providers.
 *
 * Extracted from [WebDavProvider] so URL normalization and authentication-error
 * detection can be unit-tested without an Android Context.
 */
object WebDavValidator {

    /**
     * Validate and normalize a WebDAV server URL.
     *
     * - Trims surrounding whitespace.
     * - Requires an `http://` or `https://` scheme.
     * - Ensures the URL ends with a trailing slash.
     *
     * @throws IllegalArgumentException if the URL is empty or does not use HTTP(S).
     */
    fun validateUrl(url: String): String {
        val normalized = url.trim()
        require(normalized.isNotEmpty()) { "URL must not be empty" }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "URL must start with http:// or https://"
        }
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }

    /**
     * Whether the given HTTP status code represents an authentication failure
     * (invalid credentials). Only HTTP 401 is considered an auth error.
     */
    fun isAuthenticationError(statusCode: Int): Boolean = statusCode == 401

    /**
     * Whether the given URL uses an insecure (non-encrypted) `http://` scheme.
     *
     * `http://` URLs are still accepted by [validateUrl] because LAN WebDAV
     * servers may not support TLS, but the caller (e.g. SettingsScreen) should
     * display a warning to the user when this returns `true`.
     *
     * @return `true` if the URL starts with `http://` (not `https://`).
     */
    fun isInsecureUrl(url: String): Boolean = url.trim().startsWith("http://") && !url.trim().startsWith("https://")

    /**
     * Whether the given URL points to a private/LAN host where cleartext
     * HTTP is acceptable. Returns `true` for:
     * - `localhost` or `127.0.0.1`
     * - `10.x.x.x`
     * - `192.168.x.x`
     * - `172.16-31.x.x`
     * - `*.local` mDNS hostnames
     *
     * Use this to warn the user when an `http://` URL targets a public IP.
     */
    fun isPrivateLanUrl(url: String): Boolean {
        val trimmed = url.trim()
        // Extract host from URL
        val hostPart = trimmed.substringAfter("://").substringBefore("/").substringBefore(":")
        return when {
            hostPart == "localhost" || hostPart == "127.0.0.1" -> true
            hostPart.endsWith(".local") -> true
            hostPart.startsWith("10.") -> true
            hostPart.startsWith("192.168.") -> true
            hostPart.startsWith("172.") -> {
                val secondOctet = hostPart.substringAfter("172.").substringBefore(".").toIntOrNull() ?: return false
                secondOctet in 16..31
            }
            else -> false
        }
    }

    /**
     * Whether a cleartext HTTP URL is safe (only on private/LAN).
     * Returns `true` if the URL is HTTPS, or if it's HTTP on a private LAN host.
     */
    fun isCleartextSafe(url: String): Boolean {
        return !isInsecureUrl(url) || isPrivateLanUrl(url)
    }
}
