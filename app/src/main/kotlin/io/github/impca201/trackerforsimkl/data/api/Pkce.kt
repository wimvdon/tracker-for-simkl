package io.github.impca201.trackerforsimkl.data.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636) helpers for the mobile authorization code sign-in. */
object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** A random URL-safe string: 48 bytes give a 64-character code verifier. */
    fun randomToken(bytes: Int = 48): String = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))

    /** The S256 code challenge for [verifier]. */
    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
