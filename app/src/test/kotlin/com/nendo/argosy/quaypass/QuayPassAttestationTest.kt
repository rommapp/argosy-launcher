package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import com.nendo.argosy.data.quaypass.ble.QuayPassAttestation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

class QuayPassAttestationTest {

    private val reporter = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val other = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")
    private val nonce = ByteArray(16) { (it * 7).toByte() }
    private val ts = 1_700_000_000L

    @Test
    fun `ed25519 attestation verifies and rejects tampering`() {
        val peer = QuayPassTestCrypto.newKeyPair(seed = 99)
        val preimage = QuayPassAttestation.preimage(reporter, nonce, ts)
        val sig = peer.sign(preimage)

        assertTrue(verify(peer.spki, QuayPassCredentialBundle.ALG_ED25519, preimage, sig))
        assertFalse(verify(peer.spki, QuayPassCredentialBundle.ALG_ED25519, QuayPassAttestation.preimage(other, nonce, ts), sig))
        assertFalse(verify(peer.spki, QuayPassCredentialBundle.ALG_ED25519, QuayPassAttestation.preimage(reporter, nonce, ts + 1), sig))
        val impostor = QuayPassTestCrypto.newKeyPair(seed = 100)
        assertFalse(verify(peer.spki, QuayPassCredentialBundle.ALG_ED25519, preimage, impostor.sign(preimage)))
    }

    @Test
    fun `ec p256 attestation verifies the der form`() {
        val kp = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        val preimage = QuayPassAttestation.preimage(reporter, nonce, ts)
        val sig = Signature.getInstance("SHA256withECDSA")
            .apply { initSign(kp.private); update(preimage) }
            .sign()

        assertTrue(verify(kp.public.encoded, QuayPassCredentialBundle.ALG_EC_P256, preimage, sig))
        assertFalse(verify(kp.public.encoded, QuayPassCredentialBundle.ALG_EC_P256, QuayPassAttestation.preimage(other, nonce, ts), sig))
    }

    private fun verify(spki: ByteArray, alg: Int, preimage: ByteArray, sig: ByteArray): Boolean =
        QuayPassCredentialBundle.verifyPeerAttestation(alg, spki, preimage, sig)
}
