package com.cernunnos.authenticator.util

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for [Base32Codec] (RFC 4648 Base32 encoding/decoding).
 */
class Base32Test {

    @Test
    fun decode_validInput_returnsCorrectBytes() {
        // "JBSWY3DP" decodes to the ASCII bytes of "Hello" (5 bytes, 40 bits)
        val decoded = Base32Codec.decode("JBSWY3DP")
        val expected = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f) // "Hello"
        assertArrayEquals(expected, decoded)
    }

    @Test
    fun decode_emptyString_returnsEmptyArray() {
        val decoded = Base32Codec.decode("")
        assertArrayEquals(ByteArray(0), decoded)
    }

    @Test
    fun decode_lowercaseInput_works() {
        val upper = Base32Codec.decode("JBSWY3DP")
        val lower = Base32Codec.decode("jbswy3dp")
        assertArrayEquals(upper, lower)
    }

    @Test
    fun decode_withSpaces_stripsAndDecodes() {
        val spaced = Base32Codec.decode("JBSW Y3DP")
        val plain = Base32Codec.decode("JBSWY3DP")
        assertArrayEquals(plain, spaced)
    }

    @Test
    fun decode_withDashes_stripsAndDecodes() {
        val dashed = Base32Codec.decode("JBSW-Y3DP")
        val plain = Base32Codec.decode("JBSWY3DP")
        assertArrayEquals(plain, dashed)
    }

    @Test
    fun decode_withPadding_works() {
        val padded = Base32Codec.decode("JBSWY3DP======")
        val plain = Base32Codec.decode("JBSWY3DP")
        assertArrayEquals(plain, padded)
    }

    @Test(expected = Exception::class)
    fun decode_invalidChars_throws() {
        // '1' and '8' are not in the Base32 alphabet
        Base32Codec.decode("INVALID1")
    }

    @Test
    fun encode_bytes_returnsCorrectString() {
        // "Hello" -> "JBSWY3DP"
        val data = byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f)
        assertEquals("JBSWY3DP", Base32Codec.encode(data))
    }

    @Test
    fun encode_emptyArray_returnsEmptyString() {
        assertEquals("", Base32Codec.encode(ByteArray(0)))
    }

    @Test
    fun encode_thenDecode_roundTrip() {
        val data = ByteArray(20) { (it * 7 + 3).toByte() }
        val encoded = Base32Codec.encode(data)
        val decoded = Base32Codec.decode(encoded)
        assertArrayEquals(data, decoded)
    }

    @Test
    fun encode_thenDecode_roundTrip_singleByte() {
        for (b in 0..255) {
            val data = byteArrayOf(b.toByte())
            val encoded = Base32Codec.encode(data)
            val decoded = Base32Codec.decode(encoded)
            assertArrayEquals("failed for byte $b", data, decoded)
        }
    }

    @Test
    fun decode_rfc6238TestVectors() {
        // RFC 6238 test keys. Verify round-trip of the known ASCII keys.
        // SHA1 key (20 bytes): "12345678901234567890"
        val sha1Key = "12345678901234567890".toByteArray()
        val sha1Base32 = Base32Codec.encode(sha1Key)
        assertArrayEquals(sha1Key, Base32Codec.decode(sha1Base32))
        assertEquals(20, sha1Key.size)

        // SHA256 key (32 bytes): "12345678901234567890123456789012"
        val sha256Key = "12345678901234567890123456789012".toByteArray()
        val sha256Base32 = Base32Codec.encode(sha256Key)
        assertArrayEquals(sha256Key, Base32Codec.decode(sha256Base32))
        assertEquals(32, sha256Key.size)

        // SHA512 key (64 bytes): "1234567890123456789012345678901234567890123456789012345678901234"
        val sha512Key = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()
        val sha512Base32 = Base32Codec.encode(sha512Key)
        assertArrayEquals(sha512Key, Base32Codec.decode(sha512Base32))
        assertEquals(64, sha512Key.size)
    }

    @Test
    fun encode_rfc6238TestVectors() {
        // Verify the SHA1 key encodes to a non-empty Base32 string and round-trips.
        val sha1Key = "12345678901234567890".toByteArray()
        val encoded = Base32Codec.encode(sha1Key)
        assertTrue(encoded.isNotEmpty())
        assertArrayEquals(sha1Key, Base32Codec.decode(encoded))
    }
}
