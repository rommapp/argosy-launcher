package com.nendo.argosy.data.quaypass

import android.util.Base64
import android.util.Log
import com.nendo.argosy.BuildConfig
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
            parseAndVerify(Base64.decode(base64, Base64.NO_WRAP))
        } catch (_: Throwable) {
            null
        }

        /** Verifies signature against any of the trusted server pubkeys in BuildConfig. */
        fun verifyServerSignature(signedBody: ByteArray, signature: ByteArray): Boolean {
            val pubKeysRaw = BuildConfig.QUAYPASS_SERVER_PUBKEYS
            if (pubKeysRaw.isBlank()) {
                Log.w(TAG, "QUAYPASS_SERVER_PUBKEYS empty; cannot verify credential")
                return false
            }
            for (rawPub in pubKeysRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
                val pubBytes = try {
                    Base64.decode(rawPub, Base64.NO_WRAP)
                } catch (_: Throwable) {
                    continue
                }
                if (verifyEd25519(pubBytes, signedBody, signature)) return true
            }
            return false
        }

        /** Verifies a peer payload signature using the algorithm declared in its credential. */
        fun verifyPeerSignature(
            pubkeyAlg: Int,
            pubKeyEncoded: ByteArray,
            data: ByteArray,
            sig: ByteArray
        ): Boolean = when (pubkeyAlg) {
            ALG_ED25519 -> verifyEd25519(pubKeyEncoded, data, sig)
            ALG_EC_P256 -> verifyEcP256(pubKeyEncoded, data, sig)
            else -> false
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
