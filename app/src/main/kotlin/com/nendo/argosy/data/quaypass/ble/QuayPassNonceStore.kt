package com.nendo.argosy.data.quaypass.ble

import javax.inject.Inject
import javax.inject.Singleton

/** Bounded LRU of (credentialFingerprint, nonceHex) seen within freshness window. */
@Singleton
class QuayPassNonceStore @Inject constructor() {

    private val seen = object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?): Boolean =
            size > MAX_ENTRIES
    }
    private val lock = Any()

    /** Returns true if accepted (not previously seen within window). */
    fun acceptOrReject(credentialFingerprint: String, nonce: ByteArray, nowSecs: Long): Boolean {
        val key = "$credentialFingerprint:${nonce.joinToString("") { "%02x".format(it) }}"
        synchronized(lock) {
            pruneExpired(nowSecs)
            val prior = seen[key]
            if (prior != null && nowSecs - prior < QuayPassConfig.NONCE_TTL_SECS) {
                return false
            }
            seen[key] = nowSecs
            return true
        }
    }

    private fun pruneExpired(nowSecs: Long) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            val (_, ts) = it.next()
            if (nowSecs - ts > QuayPassConfig.NONCE_TTL_SECS) it.remove() else break
        }
    }

    companion object {
        private const val MAX_ENTRIES = 1024
    }
}
