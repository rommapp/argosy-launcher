package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.QuayPassExchangeFrames
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuayPassExchangeFramesTest {

    private val envelope = ByteArray(200) { (it * 3).toByte() }
    private val att = ByteArray(70) { (it + 1).toByte() }
    private val challenge = ByteArray(16) { (it * 5).toByte() }

    @Test
    fun `profile write round-trips`() {
        val frame = QuayPassExchangeFrames.profileWrite(envelope, challenge)
        assertEquals(QuayPassExchangeFrames.MSG_PROFILE, QuayPassExchangeFrames.messageType(frame))
        val parsed = QuayPassExchangeFrames.parseProfileWrite(frame)!!
        assertArrayEquals(envelope, parsed.envelope)
        assertArrayEquals(challenge, parsed.challenge)
    }

    @Test
    fun `read response round-trips`() {
        val frame = QuayPassExchangeFrames.readResponse(envelope, att, 1_700_000_000L, challenge)
        val parsed = QuayPassExchangeFrames.parseReadResponse(frame)!!
        assertArrayEquals(envelope, parsed.envelope)
        assertArrayEquals(att, parsed.attestation)
        assertEquals(1_700_000_000L, parsed.tsSecs)
        assertArrayEquals(challenge, parsed.challenge)
    }

    @Test
    fun `attestation write round-trips`() {
        val frame = QuayPassExchangeFrames.attestationWrite(att, 1_700_000_042L)
        assertEquals(QuayPassExchangeFrames.MSG_ATTESTATION, QuayPassExchangeFrames.messageType(frame))
        val parsed = QuayPassExchangeFrames.parseAttestationWrite(frame)!!
        assertArrayEquals(att, parsed.attestation)
        assertEquals(1_700_000_042L, parsed.tsSecs)
    }

    @Test
    fun `truncated and mistyped frames reject`() {
        assertNull(QuayPassExchangeFrames.parseProfileWrite(ByteArray(3)))
        assertNull(QuayPassExchangeFrames.parseAttestationWrite(QuayPassExchangeFrames.profileWrite(envelope, challenge)))
        assertNull(QuayPassExchangeFrames.parseReadResponse(ByteArray(2)))
    }
}
