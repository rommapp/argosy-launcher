package com.nendo.argosy.data.remote.navidrome

import java.security.MessageDigest
import java.security.SecureRandom

object NavidromeAuth {
    const val API_VERSION = "1.16.1"
    const val CLIENT_NAME = "argosy"
    const val FORMAT = "json"

    private const val SALT_BYTES = 8
    private val random = SecureRandom()

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    fun token(password: String, salt: String): String =
        MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8)).toHex()

    fun authParams(username: String, password: String, salt: String): List<Pair<String, String>> = listOf(
        "u" to username,
        "t" to token(password, salt),
        "s" to salt,
        "v" to API_VERSION,
        "c" to CLIENT_NAME,
        "f" to FORMAT
    )

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
