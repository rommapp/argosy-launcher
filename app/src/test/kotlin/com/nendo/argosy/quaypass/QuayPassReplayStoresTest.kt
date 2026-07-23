package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.QuayPassConfig
import com.nendo.argosy.data.quaypass.ble.QuayPassCooldownStore
import com.nendo.argosy.data.quaypass.ble.QuayPassNonceStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuayPassReplayStoresTest {

    private val fp = "fingerprint-a"
    private val nonce = ByteArray(16) { 7 }

    @Test
    fun `nonce store accepts once then rejects replay within window`() {
        val store = QuayPassNonceStore()
        assertTrue(store.acceptOrReject(fp, nonce, 1000))
        assertFalse(store.acceptOrReject(fp, nonce, 1000))
        assertFalse(store.acceptOrReject(fp, nonce, 1000 + QuayPassConfig.NONCE_TTL_SECS - 1))
    }

    @Test
    fun `nonce store accepts a different nonce from the same peer`() {
        val store = QuayPassNonceStore()
        assertTrue(store.acceptOrReject(fp, nonce, 1000))
        assertTrue(store.acceptOrReject(fp, ByteArray(16) { 9 }, 1000))
    }

    @Test
    fun `nonce store accepts again after the window elapses`() {
        val store = QuayPassNonceStore()
        assertTrue(store.acceptOrReject(fp, nonce, 1000))
        assertTrue(store.acceptOrReject(fp, nonce, 1000 + QuayPassConfig.NONCE_TTL_SECS + 1))
    }

    @Test
    fun `cooldown holds within the window and clears after`() {
        val store = QuayPassCooldownStore()
        assertFalse(store.isWithinCooldown(fp, 1000))
        store.mark(fp, 1000)
        assertTrue(store.isWithinCooldown(fp, 1000 + QuayPassConfig.EXCHANGE_COOLDOWN_SECS - 1))
        assertFalse(store.isWithinCooldown(fp, 1000 + QuayPassConfig.EXCHANGE_COOLDOWN_SECS))
    }
}
