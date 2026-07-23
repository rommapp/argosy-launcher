package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

class QuayPassCredentialBundleTest {

    private val server = QuayPassTestCrypto.newKeyPair(seed = 1)
    private val otherServer = QuayPassTestCrypto.newKeyPair(seed = 2)
    private val device = QuayPassTestCrypto.newKeyPair(seed = 3)

    @Before
    fun setUp() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = listOf(server.spki)
    }

    @After
    fun tearDown() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = null
    }

    @Test
    fun `parses and verifies a well formed credential`() {
        val bytes = QuayPassTestCrypto.mintCredential(server, device.spki)
        val bundle = QuayPassCredentialBundle.parseAndVerify(bytes)
        assertNotNull(bundle)
        assertEquals(QuayPassCredentialBundle.VERSION_V1, bundle!!.version)
        assertEquals(QuayPassCredentialBundle.ALG_ED25519, bundle.pubkeyAlg)
    }

    @Test
    fun `rejects a tampered body`() {
        val bytes = QuayPassTestCrypto.mintCredential(server, device.spki)
        bytes[5] = (bytes[5] + 1).toByte()
        assertNull(QuayPassCredentialBundle.parseAndVerify(bytes))
    }

    @Test
    fun `rejects a credential signed by an untrusted server`() {
        val bytes = QuayPassTestCrypto.mintCredential(otherServer, device.spki)
        assertNull(QuayPassCredentialBundle.parseAndVerify(bytes))
    }

    @Test
    fun `accepts when the signing key is one of several trusted keys`() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = listOf(otherServer.spki, server.spki)
        val bytes = QuayPassTestCrypto.mintCredential(server, device.spki)
        assertNotNull(QuayPassCredentialBundle.parseAndVerify(bytes))
    }

    @Test
    fun `parses an expired credential but flags it expired`() {
        val bytes = QuayPassTestCrypto.mintCredential(
            server,
            device.spki,
            issuedAt = Instant.now().minusSeconds(40 * 24 * 3600),
            expiresAt = Instant.now().minusSeconds(24 * 3600)
        )
        val bundle = QuayPassCredentialBundle.parseAndVerify(bytes)
        assertNotNull(bundle)
        assertEquals(true, bundle!!.isExpired())
    }

    @Test
    fun `returns null for truncated input`() {
        assertNull(QuayPassCredentialBundle.parseAndVerify(ByteArray(10)))
    }

    @Test
    fun `peer signature is rejected when the declared algorithm disagrees with the key`() {
        val data = "payload".toByteArray()
        val sig = device.sign(data)
        assertEquals(
            true,
            QuayPassCredentialBundle.verifyPeerSignature(
                QuayPassCredentialBundle.ALG_ED25519, device.spki, data, sig
            )
        )
        assertEquals(
            false,
            QuayPassCredentialBundle.verifyPeerSignature(
                QuayPassCredentialBundle.ALG_EC_P256, device.spki, data, sig
            )
        )
    }
}
