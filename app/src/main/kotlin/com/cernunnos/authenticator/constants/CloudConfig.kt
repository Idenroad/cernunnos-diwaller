package com.cernunnos.authenticator.constants

/**
 * Network and cloud provider constants.
 */
object CloudConfig {
    // Timeouts (ms)
    const val TIMEOUT_SHORT = 10000L       // 10s — quick requests (MKCOL)
    const val TIMEOUT_STANDARD = 15000L    // 15s — standard API requests
    const val TIMEOUT_UPLOAD = 30000L      // 30s — upload connect
    const val TIMEOUT_DOWNLOAD = 60000L    // 60s — upload/download read

    // SFTP
    const val SFTP_DEFAULT_PORT = 22
    /**
     * Strict host key checking mode used for the FIRST connection to a new
     * host (when no host key has been pinned yet). We use "no" only to allow
     * JSch to connect once and capture the server's public key, which is then
     * pinned in EncryptedSharedPreferences. On subsequent connections the
     * pinned key is compared against the key presented by the server, and the
     * connection is aborted on mismatch (MITM protection). See
     * [com.cernunnos.authenticator.cloud.SftpProvider] for the pinning logic.
     */
    const val SFTP_STRICT_HOST_CHECKING = "no"
    const val SFTP_PREFERRED_AUTH = "password,publickey,keyboard-interactive"

    // Extensions
    const val TEMP_FILE_EXTENSION = ".tmp"

    // HTTP status codes
    const val HTTP_OK = 200
    const val HTTP_MULTI_STATUS = 207
    const val HTTP_UNAUTHORIZED = 401
    const val HTTP_SUCCESS_MIN = 200
    const val HTTP_SUCCESS_MAX = 299

    // HTTP methods
    const val METHOD_GET = "GET"
    const val METHOD_POST = "POST"
    const val METHOD_PUT = "PUT"
    const val METHOD_DELETE = "DELETE"
    const val METHOD_PATCH = "PATCH"
    const val METHOD_PROPFIND = "PROPFIND"
    const val METHOD_MKCOL = "MKCOL"
    const val METHOD_MOVE = "MOVE"

    // Headers
    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_DEPTH = "Depth"
    const val HEADER_DESTINATION = "Destination"
    const val HEADER_OVERWRITE = "Overwrite"
    const val HEADER_DROPBOX_API_ARG = "Dropbox-API-Arg"

    // Content types
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_OCTET_STREAM = "application/octet-stream"
    const val CONTENT_TYPE_XML = "application/xml"
    const val CONTENT_TYPE_MULTIPART_RELATED = "multipart/related"
    const val CONTENT_TYPE_FORM_URLENCODED = "application/x-www-form-urlencoded"
    const val CONTENT_TYPE_GDRIVE_FOLDER = "application/vnd.google-apps.folder"
    const val CHARSET_UTF8 = "UTF-8"

    // Auth
    const val AUTH_BEARER_PREFIX = "Bearer "
}
