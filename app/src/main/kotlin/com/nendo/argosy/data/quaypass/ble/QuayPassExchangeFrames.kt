package com.nendo.argosy.data.quaypass.ble

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * Protocol v3 exchange framing. The BLE meeting is a mutual challenge-response
 * over one GATT connection (A = central, B = peripheral):
 *
 *   1. A → B  write   PROFILE:     [0x01][u16 envLen][envelope_A][nA 16]
 *   2. A ← B  read    response:    [u16 envLen][envelope_B][u16 attLen][attB][tsB i64 BE][nB 16]
 *   3. A → B  write   ATTESTATION: [0x02][u16 attLen][attA][tsA i64 BE]
 *
 * `envelope` is the self-signed profile from [QuayPassWireFormat.encode] (still
 * cacheable). The attestations are computed per meeting; `attB` binds A's account
 * and nonce with B's signed time, `attA` the reverse. Everything here is offline,
 * peer-to-peer; only the resulting report reaches the server.
 */
object QuayPassExchangeFrames {

    const val MSG_PROFILE: Byte = 0x01
    const val MSG_ATTESTATION: Byte = 0x02

    const val CHALLENGE_BYTES: Int = 16

    private val secureRandom = SecureRandom()

    fun newChallenge(): ByteArray = ByteArray(CHALLENGE_BYTES).also { secureRandom.nextBytes(it) }

    fun profileWrite(envelope: ByteArray, challenge: ByteArray): ByteArray =
        ByteBuffer.allocate(1 + 2 + envelope.size + challenge.size).apply {
            put(MSG_PROFILE)
            putShort(envelope.size.toShort())
            put(envelope)
            put(challenge)
        }.array()

    data class ProfileWrite(val envelope: ByteArray, val challenge: ByteArray)

    fun parseProfileWrite(bytes: ByteArray): ProfileWrite? = frame(bytes) { buf ->
        if (buf.get() != MSG_PROFILE) return@frame null
        val envLen = buf.short.toInt() and 0xFFFF
        if (envLen <= 0 || buf.remaining() < envLen + CHALLENGE_BYTES) return@frame null
        val envelope = ByteArray(envLen).also { buf.get(it) }
        val challenge = ByteArray(CHALLENGE_BYTES).also { buf.get(it) }
        ProfileWrite(envelope, challenge)
    }

    fun readResponse(
        envelope: ByteArray,
        attestation: ByteArray,
        tsSecs: Long,
        challenge: ByteArray
    ): ByteArray =
        ByteBuffer.allocate(2 + envelope.size + 2 + attestation.size + 8 + challenge.size).apply {
            putShort(envelope.size.toShort())
            put(envelope)
            putShort(attestation.size.toShort())
            put(attestation)
            putLong(tsSecs)
            put(challenge)
        }.array()

    data class ReadResponse(
        val envelope: ByteArray,
        val attestation: ByteArray,
        val tsSecs: Long,
        val challenge: ByteArray
    )

    fun parseReadResponse(bytes: ByteArray): ReadResponse? = frame(bytes) { buf ->
        val envLen = buf.short.toInt() and 0xFFFF
        if (envLen <= 0 || buf.remaining() < envLen + 2) return@frame null
        val envelope = ByteArray(envLen).also { buf.get(it) }
        val attLen = buf.short.toInt() and 0xFFFF
        if (attLen <= 0 || buf.remaining() < attLen + 8 + CHALLENGE_BYTES) return@frame null
        val attestation = ByteArray(attLen).also { buf.get(it) }
        val tsSecs = buf.long
        val challenge = ByteArray(CHALLENGE_BYTES).also { buf.get(it) }
        ReadResponse(envelope, attestation, tsSecs, challenge)
    }

    fun attestationWrite(attestation: ByteArray, tsSecs: Long): ByteArray =
        ByteBuffer.allocate(1 + 2 + attestation.size + 8).apply {
            put(MSG_ATTESTATION)
            putShort(attestation.size.toShort())
            put(attestation)
            putLong(tsSecs)
        }.array()

    data class AttestationWrite(val attestation: ByteArray, val tsSecs: Long)

    fun parseAttestationWrite(bytes: ByteArray): AttestationWrite? = frame(bytes) { buf ->
        if (buf.get() != MSG_ATTESTATION) return@frame null
        val attLen = buf.short.toInt() and 0xFFFF
        if (attLen <= 0 || buf.remaining() < attLen + 8) return@frame null
        val attestation = ByteArray(attLen).also { buf.get(it) }
        AttestationWrite(attestation, buf.long)
    }

    fun messageType(bytes: ByteArray): Byte? = bytes.firstOrNull()

    private fun <T> frame(bytes: ByteArray, block: (ByteBuffer) -> T?): T? =
        try {
            block(ByteBuffer.wrap(bytes))
        } catch (_: BufferUnderflowException) {
            null
        }
}
