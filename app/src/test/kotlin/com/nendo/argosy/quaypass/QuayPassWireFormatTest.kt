package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import com.nendo.argosy.data.quaypass.ble.DecodeResult
import com.nendo.argosy.data.quaypass.ble.OutboundProfile
import com.nendo.argosy.data.quaypass.ble.QuayPassConfig
import com.nendo.argosy.data.quaypass.ble.QuayPassWireFormat
import com.nendo.argosy.data.quaypass.ble.colorOnlyAvatar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64

class QuayPassWireFormatTest {

    private val server = QuayPassTestCrypto.newKeyPair(seed = 11)
    private val device = QuayPassTestCrypto.newKeyPair(seed = 12)
    private val otherDevice = QuayPassTestCrypto.newKeyPair(seed = 13)
    private val otherServer = QuayPassTestCrypto.newKeyPair(seed = 14)

    private val profile = OutboundProfile(
        username = "traveler",
        displayName = "Traveler One",
        greeting = "hi there",
        lastGameTitle = "Chrono Trigger",
        lastGamePlatform = "snes",
        lastGamePlaytimeMinutes = 123,
        lastGameIgdbId = 4567L,
        avatar = colorOnlyAvatar(3)
    )

    @Before
    fun setUp() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = listOf(server.spki)
    }

    @After
    fun tearDown() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = null
    }

    private fun credB64(signer: QuayPassTestCrypto.KeyPair, expiresAt: Instant = Instant.now().plusSeconds(3600)) =
        Base64.getEncoder().encodeToString(
            QuayPassTestCrypto.mintCredential(signer, device.spki, expiresAt = expiresAt)
        )

    private fun validBytes(): ByteArray =
        QuayPassWireFormat.encode(profile, credB64(server)) { device.sign(it) }

    private fun reasonOf(result: DecodeResult): DecodeResult.Reason =
        (result as DecodeResult.Failure).reason

    @Test
    fun `round trips a full profile`() {
        val result = QuayPassWireFormat.decode(validBytes())
        assertTrue(result is DecodeResult.Success)
        val p = (result as DecodeResult.Success).profile
        assertEquals("traveler", p.username)
        assertEquals("Traveler One", p.displayName)
        assertEquals("hi there", p.greeting)
        assertEquals("Chrono Trigger", p.lastGameTitle)
        assertEquals(123, p.lastGamePlaytimeMinutes)
        assertEquals(4567L, p.lastGameIgdbId)
    }

    @Test
    fun `oversized payload is rejected`() {
        val result = QuayPassWireFormat.decode(ByteArray(QuayPassConfig.MAX_PROFILE_BYTES + 1))
        assertEquals(DecodeResult.Reason.OVERSIZED, reasonOf(result))
    }

    @Test
    fun `truncated payload is rejected`() {
        assertEquals(DecodeResult.Reason.TRUNCATED, reasonOf(QuayPassWireFormat.decode(ByteArray(8))))
    }

    @Test
    fun `corrupt length prefix is rejected`() {
        val bytes = validBytes()
        bytes[0] = 0xFF.toByte()
        bytes[1] = 0xFF.toByte()
        assertEquals(DecodeResult.Reason.BAD_LENGTHS, reasonOf(QuayPassWireFormat.decode(bytes)))
    }

    @Test
    fun `short profile length is rejected without throwing`() {
        val bytes = validBytes()
        bytes[0] = 0x00
        bytes[1] = 0x05
        assertEquals(DecodeResult.Reason.BAD_LENGTHS, reasonOf(QuayPassWireFormat.decode(bytes)))
    }

    @Test
    fun `credential from an untrusted server is rejected`() {
        val bytes = QuayPassWireFormat.encode(profile, credB64(otherServer)) { device.sign(it) }
        assertEquals(DecodeResult.Reason.CREDENTIAL_INVALID, reasonOf(QuayPassWireFormat.decode(bytes)))
    }

    @Test
    fun `expired credential is rejected`() {
        val bytes = QuayPassWireFormat.encode(
            profile,
            credB64(server, expiresAt = Instant.now().minusSeconds(60))
        ) { device.sign(it) }
        assertEquals(DecodeResult.Reason.CREDENTIAL_EXPIRED, reasonOf(QuayPassWireFormat.decode(bytes)))
    }

    @Test
    fun `peer signature from the wrong key is rejected`() {
        val bytes = QuayPassWireFormat.encode(profile, credB64(server)) { otherDevice.sign(it) }
        assertEquals(DecodeResult.Reason.PEER_SIGNATURE_INVALID, reasonOf(QuayPassWireFormat.decode(bytes)))
    }

    @Test
    fun `stale timestamp is rejected`() {
        val bytes = validBytes()
        val future = Instant.now().plusSeconds(QuayPassConfig.FRESHNESS_WINDOW_SECS + 60)
        assertEquals(DecodeResult.Reason.TIMESTAMP_OUT_OF_WINDOW, reasonOf(QuayPassWireFormat.decode(bytes, future)))
    }
}
