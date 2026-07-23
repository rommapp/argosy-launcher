package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import com.nendo.argosy.data.quaypass.ble.DecodeResult
import com.nendo.argosy.data.quaypass.ble.OutboundProfile
import com.nendo.argosy.data.quaypass.ble.QuayPassConfig
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleRaster
import com.nendo.argosy.data.quaypass.ble.QuayPassWireFormat
import com.upokecenter.cbor.CBORObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
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
        avatarRaster = ByteArray(0)
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
        assertEquals(0, p.avatarBytes.size)
    }

    @Test
    fun `round trips a 16px doodle raster`() {
        val raster = QuayPassDoodleCodec.encode(
            QuayPassDoodleRaster(16, IntArray(256) { it % 16 })
        )
        assertEquals(130, raster.size)
        val bytes = QuayPassWireFormat.encode(
            profile.copy(avatarRaster = raster), credB64(server)
        ) { device.sign(it) }
        val result = QuayPassWireFormat.decode(bytes)
        assertTrue(result is DecodeResult.Success)
        assertArrayEquals(raster, (result as DecodeResult.Success).profile.avatarBytes)
    }

    @Test
    fun `round trips a 32px doodle raster`() {
        val raster = QuayPassDoodleCodec.encode(
            QuayPassDoodleRaster(32, IntArray(1024) { (it * 7) % 16 })
        )
        assertEquals(514, raster.size)
        val bytes = QuayPassWireFormat.encode(
            profile.copy(avatarRaster = raster), credB64(server)
        ) { device.sign(it) }
        val result = QuayPassWireFormat.decode(bytes)
        assertTrue(result is DecodeResult.Success)
        assertArrayEquals(raster, (result as DecodeResult.Success).profile.avatarBytes)
    }

    @Test
    fun `avatar length overrunning the body is rejected`() {
        assertEquals(
            DecodeResult.Reason.TRUNCATED,
            reasonOf(QuayPassWireFormat.decode(forgedAvatarLenBytes(avatarLen = 300)))
        )
    }

    @Test
    fun `avatar length above the sanity cap is rejected`() {
        assertEquals(
            DecodeResult.Reason.BAD_LENGTHS,
            reasonOf(QuayPassWireFormat.decode(forgedAvatarLenBytes(avatarLen = 1000)))
        )
    }

    private fun forgedAvatarLenBytes(avatarLen: Int): ByteArray {
        val textBytes = CBORObject.NewMap().apply { Add("u", "traveler") }.EncodeToBytes()
        val profileBody = ByteBuffer.allocate(
            1 + 1 + QuayPassConfig.NONCE_BYTES + QuayPassConfig.TIMESTAMP_BYTES + 2 + 2 + textBytes.size
        ).apply {
            put(QuayPassConfig.PROTOCOL_MAJOR)
            put(QuayPassConfig.PROTOCOL_MINOR)
            put(ByteArray(QuayPassConfig.NONCE_BYTES))
            putLong(Instant.now().epochSecond)
            putShort(avatarLen.toShort())
            putShort(textBytes.size.toShort())
            put(textBytes)
        }.array()
        val credentialBytes = Base64.getDecoder().decode(credB64(server))
        val signedInput = ByteBuffer.allocate(2 + profileBody.size + 2 + credentialBytes.size).apply {
            putShort(profileBody.size.toShort())
            put(profileBody)
            putShort(credentialBytes.size.toShort())
            put(credentialBytes)
        }.array()
        return signedInput + device.sign(signedInput)
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
