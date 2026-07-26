package com.nendo.argosy.data.quaypass

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.nendo.argosy.BuildConfig
import java.util.Base64
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.UUID

/**
 * Wire layout (v1):
 *   u8  version = 1
 *   16  account_id (UUID bytes, big-endian)
 *   16  client_install_id (UUID bytes, big-endian)
 *   u8  pubkey_alg (0 = Ed25519, 1 = EC_P256)
 *   u16 pubkey_len (BE)
 *   N   pubkey_bytes (X.509 SubjectPublicKeyInfo)
 *   i64 issued_at_secs (BE)
 *   i64 expires_at_secs (BE)
 *   64  server_signature (Ed25519 over preceding bytes)
 */
data class QuayPassCredentialBundle(
    val version: Int,
    val accountId: UUID,
    val clientInstallId: UUID,
    val pubkeyAlg: Int,
    val pubkey: ByteArray,
    val issuedAt: Instant,
    val expiresAt: Instant
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    fun matches(otherPubkey: ByteArray): Boolean = pubkey.contentEquals(otherPubkey)

    companion object {
        const val VERSION_V1: Int = 1
        const val ALG_ED25519: Int = 0
        const val ALG_EC_P256: Int = 1

        const val SERVER_SIG_LEN: Int = 64

        private val OID_ED25519 = ASN1ObjectIdentifier("1.3.101.112")
        private val OID_EC_PUBLIC_KEY = ASN1ObjectIdentifier("1.2.840.10045.2.1")

        fun parseAndVerify(bytes: ByteArray): QuayPassCredentialBundle? {
            if (bytes.size < MIN_BYTES) return null
            val signedBody = bytes.copyOfRange(0, bytes.size - SERVER_SIG_LEN)
            val signature = bytes.copyOfRange(bytes.size - SERVER_SIG_LEN, bytes.size)
            if (!verifyServerSignature(signedBody, signature)) return null

            val buf = ByteBuffer.wrap(signedBody)
            val version = buf.get().toInt() and 0xFF
            if (version != VERSION_V1) return null

            val accountId = readUuid(buf) ?: return null
            val clientInstallId = readUuid(buf) ?: return null
            val pubkeyAlg = buf.get().toInt() and 0xFF
            val pubkeyLen = buf.short.toInt() and 0xFFFF
            if (pubkeyLen <= 0 || buf.remaining() < pubkeyLen + 16) return null
            val pubkey = ByteArray(pubkeyLen).also { buf.get(it) }
            val issuedAt = Instant.ofEpochSecond(buf.long)
            val expiresAt = Instant.ofEpochSecond(buf.long)

            return QuayPassCredentialBundle(
                version = version,
                accountId = accountId,
                clientInstallId = clientInstallId,
                pubkeyAlg = pubkeyAlg,
                pubkey = pubkey,
                issuedAt = issuedAt,
                expiresAt = expiresAt
            )
        }

        fun parseAndVerifyBase64(base64: String): QuayPassCredentialBundle? = try {
            parseAndVerify(Base64.getDecoder().decode(base64))
        } catch (_: Throwable) {
            null
        }

        @VisibleForTesting
        internal var trustedServerPubKeysOverride: List<ByteArray>? = null

        private fun trustedServerPubKeys(): List<ByteArray> {
            trustedServerPubKeysOverride?.let { return it }
            val pubKeysRaw = BuildConfig.QUAYPASS_SERVER_PUBKEYS
            if (pubKeysRaw.isBlank()) {
                Log.w(TAG, "QUAYPASS_SERVER_PUBKEYS empty; cannot verify credential")
                return emptyList()
            }
            return pubKeysRaw.split(",").mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) null
                else runCatching { Base64.getDecoder().decode(trimmed) }.getOrNull()
            }
        }

        /** Verifies the signature against any trusted server pubkey. */
        fun verifyServerSignature(signedBody: ByteArray, signature: ByteArray): Boolean =
            trustedServerPubKeys().any { verifyEd25519(it, signedBody, signature) }

        /** Verifies a peer payload signature, deriving the algorithm from the key's own SPKI. */
        fun verifyPeerSignature(
            pubkeyAlg: Int,
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            sig: ByteArray
        ): Boolean {
            val oid = try {
                SubjectPublicKeyInfo.getInstance(pubKeyEncoded).algorithm.algorithm
            } catch (_: Throwable) {
                return false
            }
            return when {
                oid == OID_ED25519 && pubkeyAlg == ALG_ED25519 ->
                    verifyEd25519(pubKeyEncoded, data, sig)
                oid == OID_EC_PUBLIC_KEY && pubkeyAlg == ALG_EC_P256 ->
                    verifyEcP256(pubKeyEncoded, data, sig)
                else -> false
            }
        }

        internal fun verifyEcP256(
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            sigP1363: ByteArray
        ): Boolean = try {
            val keyFactory = KeyFactory.getInstance("EC")
            val pub = keyFactory.generatePublic(X509EncodedKeySpec(pubKeyEncoded))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pub)
            verifier.update(data)
            verifier.verify(EcdsaP1363.toDer(sigP1363))
        } catch (_: Throwable) {
            false
        }

        /**
         * Verifies a per-meeting attestation, which uses the server-verifiable
         * signature form (Ed25519 raw / ECDSA ASN.1 DER) rather than the P1363
         * form the BLE envelope carries. Matches [QuayPassKeystore.signServerVerifiable]
         * so the same bytes verify on this client and on the Go server.
         */
        fun verifyPeerAttestation(
            pubkeyAlg: Int,
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            sig: ByteArray
        ): Boolean {
            val oid = try {
                SubjectPublicKeyInfo.getInstance(pubKeyEncoded).algorithm.algorithm
            } catch (_: Throwable) {
                return false
            }
            return when {
                oid == OID_ED25519 && pubkeyAlg == ALG_ED25519 ->
                    verifyEd25519(pubKeyEncoded, data, sig)
                oid == OID_EC_PUBLIC_KEY && pubkeyAlg == ALG_EC_P256 ->
                    verifyEcP256Der(pubKeyEncoded, data, sig)
                else -> false
            }
        }

        private fun verifyEcP256Der(
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            derSig: ByteArray
        ): Boolean = try {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pubKeyEncoded))
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(pub)
            verifier.update(data)
            verifier.verify(derSig)
        } catch (_: Throwable) {
            false
        }

        internal fun verifyEd25519(
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            sig: ByteArray
        ): Boolean = try {
            val spki = SubjectPublicKeyInfo.getInstance(pubKeyEncoded)
            val pub = Ed25519PublicKeyParameters(spki.publicKeyData.bytes, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pub)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(sig)
        } catch (t: Throwable) {
            Log.w(TAG, "Ed25519 verify error: ${t.javaClass.simpleName}: ${t.message}")
            false
        }

        private fun readUuid(buf: ByteBuffer): UUID? {
            if (buf.remaining() < 16) return null
            val high = buf.long
            val low = buf.long
            return UUID(high, low)
        }

        private const val MIN_BYTES =
            1 + 16 + 16 + 1 + 2 + 1 + 8 + 8 + SERVER_SIG_LEN
        private const val TAG = "QuayPassCredentialBundle"
    }
}
