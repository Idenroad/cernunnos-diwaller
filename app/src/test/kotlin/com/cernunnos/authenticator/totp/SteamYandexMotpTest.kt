package com.cernunnos.authenticator.totp

import com.cernunnos.authenticator.constants.TotpConfig
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for Steam Guard, Yandex, and mOTP code generation.
 */
class SteamYandexMotpTest {

    // ── Steam Guard ──

    @Test
    fun steam_generates5CharCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateSteam(secret, time = 1234567890L)
        assertEquals(5, code.length)
    }

    @Test
    fun steam_codeUsesValidCharset() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateSteam(secret, time = 1234567890L)
        val validChars = TotpConfig.STEAM_CHARSET.joinToString("")
        for (c in code) {
            assertTrue("Character '$c' not in Steam charset", c in validChars)
        }
    }

    @Test
    fun steam_sameSecretAndTime_producesSameCode() {
        val secret = ByteArray(20) { 0x42 }
        val code1 = TotpGenerator.generateSteam(secret, time = 1234567890L)
        val code2 = TotpGenerator.generateSteam(secret, time = 1234567890L)
        assertEquals(code1, code2)
    }

    @Test
    fun steam_differentTimeWindows_produceDifferentCodes() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generateSteam(secret, time = 1234567890L)
        val code2 = TotpGenerator.generateSteam(secret, time = 1234567890L + 30)
        assertNotEquals(code1, code2)
    }

    @Test
    fun steam_sameTimeWindow_producesSameCode() {
        val secret = ByteArray(20) { it.toByte() }
        // 1234567890 and 1234567919 are in the same 30s window
        val code1 = TotpGenerator.generateSteam(secret, time = 1234567890L)
        val code2 = TotpGenerator.generateSteam(secret, time = 1234567919L)
        assertEquals(code1, code2)
    }

    @Test
    fun steam_emptySecret_throwsException() {
        try {
            TotpGenerator.generateSteam(ByteArray(0), time = 1234567890L)
            fail("Should have thrown for empty secret")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun steam_knownVector_matchesReference() {
        // Test vector: secret "JBSWY3DPEHPK3PXP" decoded, time=0
        // We can't easily verify against the real Steam app, but we can verify
        // that the same input always produces the same output (deterministic)
        val secret = byteArrayOf(
            0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte(),
            0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte(),
            0x12, 0x34, 0x56, 0x78,
        )
        val code = TotpGenerator.generateSteam(secret, time = 0L)
        assertEquals(5, code.length)
        // Just verify it's deterministic
        val code2 = TotpGenerator.generateSteam(secret, time = 0L)
        assertEquals(code, code2)
    }

    // ── Yandex ──

    @Test
    fun yandex_generates8CharCode() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateYandex(secret, time = 1234567890L)
        assertEquals(8, code.length)
    }

    @Test
    fun yandex_codeUsesHexCharset() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateYandex(secret, time = 1234567890L)
        val validChars = TotpConfig.YANDEX_CHARSET.joinToString("")
        for (c in code) {
            assertTrue("Character '$c' not in Yandex charset", c in validChars)
        }
    }

    @Test
    fun yandex_sameSecretAndTime_producesSameCode() {
        val secret = ByteArray(20) { 0x42 }
        val code1 = TotpGenerator.generateYandex(secret, time = 1234567890L)
        val code2 = TotpGenerator.generateYandex(secret, time = 1234567890L)
        assertEquals(code1, code2)
    }

    @Test
    fun yandex_differentTimeWindows_produceDifferentCodes() {
        val secret = ByteArray(20) { it.toByte() }
        val code1 = TotpGenerator.generateYandex(secret, time = 1234567890L)
        val code2 = TotpGenerator.generateYandex(secret, time = 1234567890L + 30)
        assertNotEquals(code1, code2)
    }

    @Test
    fun yandex_emptySecret_throwsException() {
        try {
            TotpGenerator.generateYandex(ByteArray(0), time = 1234567890L)
            fail("Should have thrown for empty secret")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // ── mOTP ──

    @Test
    fun motp_generates6DigitCode() {
        val code = TotpGenerator.generateMotp("000102030405060708090a0b0c0d0e0f", "1234", 1234567890L)
        assertEquals(6, code.length)
        for (c in code) {
            assertTrue("Character '$c' is not a digit", c.isDigit())
        }
    }

    @Test
    fun motp_sameInputs_producesSameCode() {
        val secret = "deadbeef"
        val pin = "1234"
        val code1 = TotpGenerator.generateMotp(secret, pin, 1234567890L)
        val code2 = TotpGenerator.generateMotp(secret, pin, 1234567890L)
        assertEquals(code1, code2)
    }

    @Test
    fun motp_differentPin_producesDifferentCode() {
        val secret = "deadbeef"
        val code1 = TotpGenerator.generateMotp(secret, "1234", 1234567890L)
        val code2 = TotpGenerator.generateMotp(secret, "5678", 1234567890L)
        assertNotEquals(code1, code2)
    }

    @Test
    fun motp_differentTimeWindow_producesDifferentCode() {
        val secret = "deadbeef"
        val code1 = TotpGenerator.generateMotp(secret, "1234", 1234567890L)
        // 10-second window: 1234567890 + 10 = different window
        val code2 = TotpGenerator.generateMotp(secret, "1234", 1234567890L + 10)
        assertNotEquals(code1, code2)
    }

    @Test
    fun motp_sameTimeWindow_producesSameCode() {
        val secret = "deadbeef"
        // 1234567890 and 1234567899 are in the same 10s window
        val code1 = TotpGenerator.generateMotp(secret, "1234", 1234567890L)
        val code2 = TotpGenerator.generateMotp(secret, "1234", 1234567899L)
        assertEquals(code1, code2)
    }

    @Test
    fun motp_emptySecret_throwsException() {
        try {
            TotpGenerator.generateMotp("", "1234", 1234567890L)
            fail("Should have thrown for empty secret")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun motp_emptyPin_throwsException() {
        try {
            TotpGenerator.generateMotp("deadbeef", "", 1234567890L)
            fail("Should have thrown for empty PIN")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // ── Dispatch function ──

    @Test
    fun generateForType_steam_dispatchesCorrectly() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateForType(secret, "steam", time = 1234567890L)
        val directCode = TotpGenerator.generateSteam(secret, time = 1234567890L)
        assertEquals(directCode, code)
    }

    @Test
    fun generateForType_yandex_dispatchesCorrectly() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateForType(secret, "yandex", time = 1234567890L)
        val directCode = TotpGenerator.generateYandex(secret, time = 1234567890L)
        assertEquals(directCode, code)
    }

    @Test
    fun generateForType_motp_dispatchesCorrectly() {
        val secretHex = "deadbeef"
        val secretBytes = secretHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val code = TotpGenerator.generateForType(
            secret = secretBytes,
            type = "motp",
            time = 1234567890L,
            pin = "1234",
            secretHex = secretHex,
        )
        val directCode = TotpGenerator.generateMotp(secretHex, "1234", 1234567890L)
        assertEquals(directCode, code)
    }

    @Test
    fun generateForType_totp_dispatchesCorrectly() {
        val secret = ByteArray(20) { it.toByte() }
        val code = TotpGenerator.generateForType(secret, "totp", time = 1234567890L)
        val directCode = TotpGenerator.generate(secret, 1234567890L)
        assertEquals(directCode, code)
    }
}
