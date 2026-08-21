package android.net

import java.net.URLDecoder

/**
 * JVM test stub for android.net.Uri.
 * Minimal implementation supporting otpauth:// URI parsing
 * (scheme, host, path, query parameters).
 */
class Uri private constructor(
    private val scheme: String?,
    private val host: String?,
    private val path: String?,
    private val query: String?,
) {

    fun getScheme(): String? = scheme
    fun getHost(): String? = host
    fun getPath(): String? = path

    private val queryParams: Map<String, String> by lazy {
        if (query.isNullOrEmpty()) emptyMap()
        else query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx < 0) null
            else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()
    }

    val queryParameterNames: Set<String>
        get() = queryParams.keys

    fun getQueryParameter(name: String): String? = queryParams[name]

    override fun toString(): String {
        val sb = StringBuilder()
        scheme?.let { sb.append(it).append("://") }
        host?.let { sb.append(it) }
        path?.let { sb.append(it) }
        query?.let { sb.append("?").append(it) }
        return sb.toString()
    }

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri {
            // Format: scheme://host/path?query
            val schemeEnd = uriString.indexOf("://")
            if (schemeEnd < 0) return Uri(null, null, null, null)
            val scheme = uriString.substring(0, schemeEnd)
            val rest = uriString.substring(schemeEnd + 3)
            // host ends at first / or ? or end
            val hostEnd = rest.indexOfFirst { it == '/' || it == '?' }
            val host: String?
            val remainder: String
            if (hostEnd < 0) {
                host = rest
                remainder = ""
            } else {
                host = rest.substring(0, hostEnd)
                remainder = rest.substring(hostEnd)
            }
            val path: String?
            val query: String?
            val qIdx = remainder.indexOf('?')
            if (qIdx < 0) {
                path = remainder
                query = null
            } else {
                path = remainder.substring(0, qIdx)
                query = remainder.substring(qIdx + 1)
            }
            return Uri(scheme, host, path, query)
        }
    }
}
