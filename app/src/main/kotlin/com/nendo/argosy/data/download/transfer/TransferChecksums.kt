package com.nendo.argosy.data.download.transfer

import java.security.MessageDigest
import java.util.Base64
import okhttp3.Headers

internal class TransferChecksums private constructor(
    private val checks: List<Check>,
    val representationChecksum: String?
) {
    private class Check(val digest: MessageDigest, val expected: ByteArray, val representation: Boolean)

    val needsPrefix: Boolean get() = checks.any { it.representation }
    val hasUnprotectedSegmentChecksums: Boolean
        get() = representationChecksum == null && checks.any { !it.representation }

    fun updatePrefix(bytes: ByteArray, count: Int) {
        checks.filter { it.representation }.forEach { it.digest.update(bytes, 0, count) }
    }

    fun update(bytes: ByteArray, count: Int) {
        checks.forEach { it.digest.update(bytes, 0, count) }
    }

    fun matches(): Boolean = checks.all { MessageDigest.isEqual(it.expected, it.digest.digest()) }

    companion object {
        fun parse(
            headers: Headers,
            completeRepresentation: Boolean,
            partial: Boolean,
            retained: String?,
            fullBody: Boolean = !partial
        ): TransferChecksums {
            val checks = mutableListOf<Check>()
            val representationChecks = sortedMapOf<String, String>()
            fun add(algorithm: String, encoded: String, representation: Boolean) {
                val name = when (algorithm.lowercase()) {
                    "sha-256" -> "SHA-256"
                    "md5" -> "MD5"
                    else -> return
                }
                val expected = try {
                    Base64.getDecoder().decode(encoded)
                } catch (_: IllegalArgumentException) {
                    throw InvalidTransferHeaders("Invalid checksum encoding")
                }
                val digest = MessageDigest.getInstance(name)
                if (expected.size != digest.digestLength) throw InvalidTransferHeaders("Invalid checksum length")
                if (representation) {
                    val token = algorithm.lowercase()
                    val canonical = Base64.getEncoder().encodeToString(expected)
                    val previous = representationChecks.put(token, canonical)
                    if (previous != null && previous != canonical) throw InvalidTransferHeaders("Changed representation checksum")
                } else checks += Check(digest, expected, false)
            }
            if (partial && retained != null) {
                for (value in retained.split(';')) {
                    val parts = value.split('=', limit = 2)
                    if (parts.size != 2 || parts[0] !in setOf("sha-256", "md5")) {
                        throw InvalidTransferHeaders("Invalid retained checksum")
                    }
                    add(parts[0], parts[1], true)
                }
            }
            val md5 = headers.values("Content-MD5")
            if (md5.size > 1) throw InvalidTransferHeaders("Duplicate Content-MD5")
            md5.singleOrNull()?.let { add("md5", it, fullBody) }
            for (header in listOf("Content-Digest", "Repr-Digest", "Digest")) {
                val seen = mutableSetOf<String>()
                for (value in headers.values(header).flatMap { it.split(',') }) {
                    val parts = value.trim().split('=', limit = 2)
                    if (parts.size != 2 || !seen.add(parts[0].lowercase())) {
                        throw InvalidTransferHeaders("Invalid $header")
                    }
                    val algorithm = parts[0].trim()
                    if (algorithm.lowercase() !in setOf("sha-256", "md5")) continue
                    val encoded = parts[1].trim()
                    val digest = if (header == "Digest") encoded else {
                        if (!encoded.startsWith(':') || !encoded.endsWith(':') || encoded.length < 2) {
                            throw InvalidTransferHeaders("Invalid $header")
                        }
                        encoded.substring(1, encoded.lastIndex)
                    }
                    add(algorithm, digest, fullBody || header != "Content-Digest")
                }
            }
            if (completeRepresentation) {
                for ((algorithm, encoded) in representationChecks) {
                    val digest = MessageDigest.getInstance(if (algorithm == "md5") "MD5" else "SHA-256")
                    checks += Check(digest, Base64.getDecoder().decode(encoded), true)
                }
            }
            val serialized = representationChecks.entries.joinToString(";") { "${it.key}=${it.value}" }
            return TransferChecksums(checks, serialized.takeIf { it.isNotEmpty() })
        }
    }
}
