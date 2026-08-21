package com.cernunnos.authenticator.security

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * JVM unit tests verifying that all English string resources have
 * corresponding French translations.
 *
 * This is a critical test that catches missing translations before
 * release. It reads the actual XML files from the file system and
 * compares the key sets.
 */
class LocalizationCompletenessTest {

    private val enFile = File("src/main/res/values/strings.xml")
    private val frFile = File("src/main/res/values-fr/strings.xml")

    // Regex to extract <string name="key"> entries
    private val stringPattern = Regex("""<string\s+name="([^"]+)">""")

    // Regex to extract <string-array name="key"> entries
    private val stringArrayPattern = Regex("""<string-array\s+name="([^"]+)">""")

    // Regex to extract format specifiers like %1$d, %1$s, %2$d, etc.
    private val formatSpecifierPattern = Regex("""%\d+\$[ds]""")

    @Test
    fun allStringKeys_existInFrench() {
        val enText = enFile.readText()
        val frText = frFile.readText()

        val enKeys = stringPattern.findAll(enText).map { it.groupValues[1] }.toSet()
        val frKeys = stringPattern.findAll(frText).map { it.groupValues[1] }.toSet()

        val missing = enKeys - frKeys
        assertTrue(
            "Missing French translations for string keys: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun allStringArrays_existInFrench() {
        val enText = enFile.readText()
        val frText = frFile.readText()

        val enArrays = stringArrayPattern.findAll(enText).map { it.groupValues[1] }.toSet()
        val frArrays = stringArrayPattern.findAll(frText).map { it.groupValues[1] }.toSet()

        val missing = enArrays - frArrays
        assertTrue(
            "Missing French translations for string-array keys: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun formatStrings_haveMatchingFormatInFrench() {
        val enText = enFile.readText()
        val frText = frFile.readText()

        // Build a map of key -> full string element content for EN
        val enStrings = parseStringEntries(enText)
        val frStrings = parseStringEntries(frText)

        val mismatches = mutableListOf<String>()

        for ((key, enValue) in enStrings) {
            val frValue = frStrings[key] ?: continue // missing keys are caught by the other test
            val enFormats = formatSpecifierPattern.findAll(enValue).map { it.value }.toSet()
            val frFormats = formatSpecifierPattern.findAll(frValue).map { it.value }.toSet()

            if (enFormats != frFormats) {
                mismatches.add(
                    "$key: EN has $enFormats but FR has $frFormats",
                )
            }
        }

        assertTrue(
            "Format specifier mismatches between EN and FR: ${mismatches.joinToString("; ")}",
            mismatches.isEmpty(),
        )
    }

    @Test
    fun noDuplicateStringKeys_inEnglish() {
        val enText = enFile.readText()
        val enKeys = stringPattern.findAll(enText).map { it.groupValues[1] }.toList()

        val duplicates = enKeys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(
            "Duplicate string keys in values/strings.xml: $duplicates",
            duplicates.isEmpty(),
        )
    }

    @Test
    fun noDuplicateStringKeys_inFrench() {
        val frText = frFile.readText()
        val frKeys = stringPattern.findAll(frText).map { it.groupValues[1] }.toList()

        val duplicates = frKeys.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue(
            "Duplicate string keys in values-fr/strings.xml: $duplicates",
            duplicates.isEmpty(),
        )
    }

    // ── Helpers ──

    /**
     * Parse <string name="key">value</string> entries from the XML text
     * and return a map of key -> raw value text (including format specifiers).
     */
    private fun parseStringEntries(xmlText: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // Match the full <string name="key">...</string> element
        val fullElementPattern = Regex("""<string\s+name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        fullElementPattern.findAll(xmlText).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            result[key] = value
        }
        return result
    }
}
