package com.cernunnos.authenticator.security

import android.content.ContextWrapper
import com.cernunnos.authenticator.p2p.WifiDirectManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for Wi-Fi Direct security constants and the public API of
 * [WifiDirectManager].
 *
 * The MAGIC_HANDSHAKE and P2P_PORT constants are private instance fields on
 * [WifiDirectManager]. To read them we need an instance, which requires a
 * Context. We use a [ContextWrapper] with a null base and override
 * `getSystemService` to return null so the constructor completes without
 * needing the real Android Wi-Fi Direct framework. The handshake/port fields
 * are initialized unconditionally in the constructor regardless of whether
 * the framework is available.
 */
class WifiDirectSecurityTest {

    /**
     * A minimal Context that lets [WifiDirectManager] be instantiated in a
     * pure-JVM test. Only `getSystemService` is invoked by the constructor;
     * it returns null so `manager` is null (the class handles that case).
     */
    private val context: android.content.Context = object : ContextWrapper(null) {
        override fun getSystemService(name: String): Any? = null
    }

    private val manager: WifiDirectManager = WifiDirectManager(context)

    private val magicHandshake: ByteArray =
        WifiDirectManager::class.java.getDeclaredField("MAGIC_HANDSHAKE").apply { isAccessible = true }
            .get(manager) as ByteArray

    private val p2pPort: Int =
        WifiDirectManager::class.java.getDeclaredField("P2P_PORT").apply { isAccessible = true }
            .get(manager) as Int

    @Test
    fun magicHandshake_isNotEmptyAndAtLeast4Bytes() {
        assertNotNull(magicHandshake)
        assertTrue(
            "MAGIC_HANDSHAKE must not be empty",
            magicHandshake.isNotEmpty(),
        )
        assertTrue(
            "MAGIC_HANDSHAKE must be at least 4 bytes (was ${magicHandshake.size})",
            magicHandshake.size >= 4,
        )
    }

    @Test
    fun p2pPort_isValidPortNumber() {
        assertTrue(
            "P2P_PORT must be in range 1..65535 (was $p2pPort)",
            p2pPort in 1..65535,
        )
    }

    @Test
    fun magicHandshake_matchesCernunnosSequence() {
        // The Cernunnos magic sequence: 0xC0 0xC0 followed by the ASCII bytes
        // of "rnunnos" (so the full sequence reads "C0C0rnunnos" — a stylized
        // encoding of "Cernunnos").
        val expected = byteArrayOf(
            0xC0.toByte(), 0xC0.toByte(),
            0x72.toByte(), 0x6E.toByte(), 0x75.toByte(),
            0x6E.toByte(), 0x6E.toByte(), 0x6F.toByte(), 0x73.toByte(),
        )
        assertEquals(
            "MAGIC_HANDSHAKE must match the Cernunnos magic sequence",
            expected.toList(),
            magicHandshake.toList(),
        )
    }

    @Test
    fun wifiDirectManager_exposesSendAndReceiveMethods() {
        // Verify the class declares the expected public transfer methods so
        // that the encrypted send/receive API surface is preserved.
        val methods = WifiDirectManager::class.java.declaredMethods.map { it.name }
        assertTrue(
            "WifiDirectManager must declare sendEncryptedData (found: $methods)",
            methods.contains("sendEncryptedData"),
        )
        assertTrue(
            "WifiDirectManager must declare receiveEncryptedData (found: $methods)",
            methods.contains("receiveEncryptedData"),
        )
    }
}
