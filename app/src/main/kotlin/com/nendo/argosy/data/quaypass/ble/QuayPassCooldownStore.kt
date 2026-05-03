package com.nendo.argosy.data.quaypass.ble

import javax.inject.Inject
import javax.inject.Singleton

/** 12-hour cooldown keyed on credential pubkey fingerprint. Bounded LRU. */
@Singleton
class QuayPassCooldownStore @Inject constructor() {

    private val recent = object : LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?): Boolean =
            size > MAX_ENTRIES
    }
    private val lock = Any()

    /** True if the peer is within cooldown and should be skipped. */
    fun isWithinCooldown(credentialFingerprint: String, nowSecs: Long): Boolean {
        synchronized(lock) {
            val last = recent[credentialFingerprint] ?: return false
            return nowSecs - last < QuayPassConfig.EXCHANGE_COOLDOWN_SECS
        }
    }

    fun mark(credentialFingerprint: String, nowSecs: Long) {
        synchronized(lock) {
            recent[credentialFingerprint] = nowSecs
        }
    }

    fun clear() {
        synchronized(lock) { recent.clear() }
    }

    companion object {
        private const val MAX_ENTRIES = 2048
    }
}
