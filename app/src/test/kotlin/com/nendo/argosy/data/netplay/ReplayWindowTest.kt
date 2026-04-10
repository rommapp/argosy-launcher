package com.nendo.argosy.data.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayWindowTest {

    private fun nonce(i: Int): ByteArray = ByteArray(24) { (i + it).toByte() }

    @Test
    fun freshNonces_accepted() {
        val w = ReplayWindow(capacity = 16)
        assertTrue(w.checkAndRecord(nonce(1)))
        assertTrue(w.checkAndRecord(nonce(2)))
        assertTrue(w.checkAndRecord(nonce(3)))
    }

    @Test
    fun duplicateNonce_rejected() {
        val w = ReplayWindow(capacity = 16)
        val n = nonce(42)
        assertTrue(w.checkAndRecord(n))
        assertFalse(w.checkAndRecord(n))
        assertFalse(w.checkAndRecord(n.copyOf()))
    }

    @Test
    fun lruEviction_respectsCapacity() {
        val capacity = 8
        val w = ReplayWindow(capacity = capacity)
        repeat(capacity * 3) { i ->
            assertTrue(w.checkAndRecord(nonce(i)))
        }
        assertEquals(capacity, w.size())
    }

    @Test
    fun afterEviction_oldNonceCanReappear() {
        val capacity = 4
        val w = ReplayWindow(capacity = capacity)
        val first = nonce(1)
        w.checkAndRecord(first)
        repeat(capacity + 2) { i -> w.checkAndRecord(nonce(100 + i)) }
        assertTrue("old nonce evicted, accepted again", w.checkAndRecord(first))
    }
}
