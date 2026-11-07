package de.ulf.dashboard

import java.security.MessageDigest
import java.util.Base64

/** HTTP Basic auth check for the settings portal: fixed user [USER], password supplied by the caller. */
object BasicAuth {
    const val USER = "admin"
    private const val SCHEME = "Basic "

    fun isAuthorized(authorizationHeader: String?, password: String): Boolean {
        if (authorizationHeader == null || !authorizationHeader.startsWith(SCHEME, ignoreCase = true)) return false
        val credentials = try {
            Base64.getDecoder().decode(authorizationHeader.substring(SCHEME.length).trim())
        } catch (e: IllegalArgumentException) {
            return false // not valid base64
        }
        // Constant-time comparison so response timing does not leak the password
        return MessageDigest.isEqual(credentials, "$USER:$password".toByteArray(Charsets.UTF_8))
    }
}
