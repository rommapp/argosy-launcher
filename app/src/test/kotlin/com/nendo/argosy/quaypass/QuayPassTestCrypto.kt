package com.nendo.argosy.quaypass

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/** In-process Ed25519 mint + sign helpers mirroring the server and device. */
object QuayPassTestCrypto {

    class KeyPair(val priv: Ed25519PrivateKeyParameters, val pub: Ed25519PublicKeyParameters) {
        val spki: ByteArray get() = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(pub).encoded
        fun sign(data: ByteArray): ByteArray = Ed25519Signer().run {
            init(true, priv)
            update(data, 0, data.size)
            generateSignature()
        }
    }

    fun newKeyPair(seed: Long): KeyPair {
        val rng = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) }
        val gen = Ed25519KeyPairGenerator().apply {
            init(Ed25519KeyGenerationParameters(rng))
        }
        val kp = gen.generateKeyPair()
        return KeyPair(
            kp.private as Ed25519PrivateKeyParameters,
            kp.public as Ed25519PublicKeyParameters
        )
    }

    /** Builds a server-signed credential bundle for the given device pubkey. */
    fun mintCredential(
        server: KeyPair,
        deviceSpki: ByteArray,
        accountId: UUID = UUID.randomUUID(),
        installId: UUID = UUID.randomUUID(),
        issuedAt: Instant = Instant.now().minusSeconds(60),
        expiresAt: Instant = Instant.now().plusSeconds(14L * 24 * 3600),
        pubkeyAlg: Int = 0
    ): ByteArray {
        val body = ByteBuffer.allocate(1 + 16 + 16 + 1 + 2 + deviceSpki.size + 8 + 8).apply {
            put(1.toByte())
            putLong(accountId.mostSignificantBits)
            putLong(accountId.leastSignificantBits)
            putLong(installId.mostSignificantBits)
            putLong(installId.leastSignificantBits)
            put(pubkeyAlg.toByte())
            putShort(deviceSpki.size.toShort())
            put(deviceSpki)
            putLong(issuedAt.epochSecond)
            putLong(expiresAt.epochSecond)
        }.array()
        return body + server.sign(body)
    }
}
