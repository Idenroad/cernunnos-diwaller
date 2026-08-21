package com.cernunnos.authenticator.constants

import java.util.Locale

/**
 * Date format patterns and locale.
 */
object DateFormatConfig {
    const val DATE_FORMAT_BACKUP = "yyyyMMdd_HHmmss"
    const val DATE_FORMAT_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz"
    const val DATE_FORMAT_ISO_WITH_MILLIS = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    const val DATE_FORMAT_ISO = "yyyy-MM-dd'T'HH:mm:ssXXX"
    const val DATE_FORMAT_TIME = "HH:mm:ss"

    val LOCALE: Locale = Locale.US
}
