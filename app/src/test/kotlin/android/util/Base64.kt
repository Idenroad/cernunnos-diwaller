package android.util

import java.util.Base64 as JBase64

/**
 * JVM test stub for android.util.Base64.
 * Mirrors the real Android API: a class with static methods and int flags.
 * Supports the flags used by the app: URL_SAFE, NO_PADDING, NO_WRAP.
 */
class Base64 {
    companion object {
        const val DEFAULT = 0
        const val NO_PADDING = 1
        const val NO_WRAP = 2
        const val URL_SAFE = 8

        @JvmStatic
        fun encodeToString(input: ByteArray, flags: Int): String {
            var enc = JBase64.getEncoder()
            if (flags and URL_SAFE != 0) {
                enc = JBase64.getUrlEncoder()
            }
            if (flags and NO_PADDING != 0) {
                enc = enc.withoutPadding()
            }
            return enc.encodeToString(input)
        }

        @JvmStatic
        fun decode(str: String, flags: Int): ByteArray {
            var dec = JBase64.getDecoder()
            if (flags and URL_SAFE != 0) {
                dec = JBase64.getUrlDecoder()
            }
            return dec.decode(str)
        }

        @JvmStatic
        fun encode(input: ByteArray, flags: Int): ByteArray {
            var enc = JBase64.getEncoder()
            if (flags and URL_SAFE != 0) {
                enc = JBase64.getUrlEncoder()
            }
            if (flags and NO_PADDING != 0) {
                enc = enc.withoutPadding()
            }
            return enc.encode(input)
        }
    }
}
