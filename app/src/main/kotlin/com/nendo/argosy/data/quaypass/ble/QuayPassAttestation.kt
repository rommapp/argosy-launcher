package com.nendo.argosy.data.quaypass.ble

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Per-meeting attestation (protocol v3). A peer proves it was live in this
 * meeting by signing, with its install key, a domain-separated preimage that
 * binds the REPORTER's account, the reporter's fresh challenge nonce, and the
 * PEER's own clock:
 *
 *   "quaypass-att-v1" ‖ reporterAccountID[16 BE] ‖ nonce ‖ ts_i64_BE
 *
 * The reporter binding stops a third party who captured the air from claiming
 * the meeting as their own; the peer-signed `ts` stops the reporter from
 * spreading one meeting across many 12h windows for many tickets. The signature
 * is the server-verifiable form (Ed25519 raw / ECDSA DER), so both the peer
 * client and the Go server verify it with stock crypto.
 */
object QuayPassAttestation {

    const val DOMAIN_TAG = "quaypass-att-v1"
    private val DOMAIN_TAG_BYTES = DOMAIN_TAG.toByteArray(Charsets.US_ASCII)

    fun preimage(reporterAccountId: UUID, nonce: ByteArray, tsSecs: Long): ByteArray =
        ByteBuffer.allocate(DOMAIN_TAG_BYTES.size + 16 + nonce.size + 8).apply {
            put(DOMAIN_TAG_BYTES)
            putLong(reporterAccountId.mostSignificantBits)
            putLong(reporterAccountId.leastSignificantBits)
            put(nonce)
            putLong(tsSecs)
        }.array()

    /**
     * Verifies a peer's attestation against the public key in its credential bundle.
     */
    fun verify(
        peer: QuayPassCredentialBundle,
        reporterAccountId: UUID,
        nonce: ByteArray,
        tsSecs: Long,
        signature: ByteArray
    ): Boolean = QuayPassCredentialBundle.verifyPeerAttestation(
        peer.pubkeyAlg,
        peer.pubkey,
        preimage(reporterAccountId, nonce, tsSecs),
        signature
    )
}
