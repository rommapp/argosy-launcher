package com.nendo.argosy.data.quaypass.ble

import android.util.Base64
import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import com.upokecenter.cbor.CBORObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

data class OutboundProfile(
    val username: String,
    val displayName: String?,
    val greeting: String?,
    val lastGameTitle: String?,
    val lastGamePlatform: String?,
    val lastGamePlaytimeMinutes: Int?,
    val lastGameIgdbId: Long?,
    val avatar: QuayPassAvatar
)

data class InboundProfile(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val nonce: ByteArray,
    val timestamp: Instant,
    val avatar: QuayPassAvatar,
    val avatarBytes: ByteArray,
    val username: String,
    val displayName: String?,
    val greeting: String?,
    val lastGameTitle: String?,
    val lastGamePlatform: String?,
    val lastGamePlaytimeMinutes: Int?,
    val lastGameIgdbId: Long?,
    val credentialBundle: QuayPassCredentialBundle,
    val credentialFingerprint: String
)

sealed class DecodeResult {
    data class Success(val profile: InboundProfile) : DecodeResult()
    data class Failure(val reason: Reason) : DecodeResult()

    enum class Reason {
        OVERSIZED,
        TRUNCATED,
        UNKNOWN_PROTOCOL_MAJOR,
        BAD_LENGTHS,
        CREDENTIAL_INVALID,
        CREDENTIAL_EXPIRED,
        PEER_SIGNATURE_INVALID,
        TIMESTAMP_OUT_OF_WINDOW,
        TEXT_OVERSIZED,
        TEXT_FIELD_TOO_LONG,
        TEXT_PARSE_FAILED,
        REQUIRED_FIELD_MISSING
    }
}

object QuayPassWireFormat {

    private val secureRandom = SecureRandom()

    /**
     * Build the on-the-wire bytes for our profile, ready to write to a peer's
     * GATT characteristic. Caller supplies a [signer] that returns an Ed25519
     * signature over the input bytes using the install's private key.
     */
    fun encode(
        profile: OutboundProfile,
        credentialBytesBase64: String,
        signer: (ByteArray) -> ByteArray
    ): ByteArray {
        val nonce = ByteArray(QuayPassConfig.NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val timestamp = Instant.now().epochSecond
        val avatarBlock = QuayPassAvatarCodec.encode(profile.avatar)
        val textBytes = encodeText(profile)
        require(textBytes.size <= QuayPassConfig.MAX_TEXT_BYTES) {
            "QuayPass text section too large: ${textBytes.size} > ${QuayPassConfig.MAX_TEXT_BYTES}"
        }

        val profileBody = ByteBuffer.allocate(
            1 + 1 + QuayPassConfig.NONCE_BYTES + QuayPassConfig.TIMESTAMP_BYTES +
                QuayPassConfig.AVATAR_BLOCK_BYTES + 2 + textBytes.size
        ).apply {
            put(QuayPassConfig.PROTOCOL_MAJOR)
            put(QuayPassConfig.PROTOCOL_MINOR)
            put(nonce)
            putLong(timestamp)
            put(avatarBlock)
            putShort(textBytes.size.toShort())
            put(textBytes)
        }.array()

        val credentialBytes = Base64.decode(credentialBytesBase64, Base64.NO_WRAP)

        val signedInput = ByteBuffer.allocate(2 + profileBody.size + 2 + credentialBytes.size).apply {
            putShort(profileBody.size.toShort())
            put(profileBody)
            putShort(credentialBytes.size.toShort())
            put(credentialBytes)
        }.array()

        require(signedInput.size + QuayPassConfig.SIGNATURE_BYTES <= QuayPassConfig.MAX_PROFILE_BYTES) {
            "QuayPass payload too large: ${signedInput.size + QuayPassConfig.SIGNATURE_BYTES}"
        }

        val signature = signer(signedInput)
        require(signature.size == QuayPassConfig.SIGNATURE_BYTES) {
            "Unexpected signature length: ${signature.size}"
        }

        return signedInput + signature
    }

    /** Strict receive path. Failure is silent to the peer; reason returned for logging only. */
    fun decode(bytes: ByteArray, now: Instant = Instant.now()): DecodeResult {
        if (bytes.size > QuayPassConfig.MAX_PROFILE_BYTES) {
            return DecodeResult.Failure(DecodeResult.Reason.OVERSIZED)
        }
        if (bytes.size < MIN_VALID_BYTES) {
            return DecodeResult.Failure(DecodeResult.Reason.TRUNCATED)
        }

        val sigStart = bytes.size - QuayPassConfig.SIGNATURE_BYTES
        val signedInput = bytes.copyOfRange(0, sigStart)
        val signature = bytes.copyOfRange(sigStart, bytes.size)

        val buf = ByteBuffer.wrap(signedInput)
        val profileLen = buf.short.toInt() and 0xFFFF
        if (profileLen <= 0 || buf.remaining() < profileLen + 2) {
            return DecodeResult.Failure(DecodeResult.Reason.BAD_LENGTHS)
        }
        val profileBody = ByteArray(profileLen).also { buf.get(it) }
        val credentialLen = buf.short.toInt() and 0xFFFF
        if (credentialLen <= 0 || buf.remaining() != credentialLen) {
            return DecodeResult.Failure(DecodeResult.Reason.BAD_LENGTHS)
        }
        val credentialBytes = ByteArray(credentialLen).also { buf.get(it) }

        val credential = QuayPassCredentialBundle.parseAndVerify(credentialBytes)
            ?: return DecodeResult.Failure(DecodeResult.Reason.CREDENTIAL_INVALID)
        if (credential.isExpired(now)) {
            return DecodeResult.Failure(DecodeResult.Reason.CREDENTIAL_EXPIRED)
        }
        if (!QuayPassCredentialBundle.verifyPeerSignature(
                credential.pubkeyAlg, credential.pubkey, signedInput, signature
            )
        ) {
            return DecodeResult.Failure(DecodeResult.Reason.PEER_SIGNATURE_INVALID)
        }

        val pb = ByteBuffer.wrap(profileBody)
        val major = (pb.get().toInt() and 0xFF)
        val minor = (pb.get().toInt() and 0xFF)
        if (major != QuayPassConfig.PROTOCOL_MAJOR.toInt()) {
            return DecodeResult.Failure(DecodeResult.Reason.UNKNOWN_PROTOCOL_MAJOR)
        }
        val nonce = ByteArray(QuayPassConfig.NONCE_BYTES).also { pb.get(it) }
        val timestampSecs = pb.long
        val timestampInstant = Instant.ofEpochSecond(timestampSecs)
        val drift = now.epochSecond - timestampSecs
        if (drift > QuayPassConfig.FRESHNESS_WINDOW_SECS || drift < -QuayPassConfig.FRESHNESS_WINDOW_SECS) {
            return DecodeResult.Failure(DecodeResult.Reason.TIMESTAMP_OUT_OF_WINDOW)
        }
        val avatarBytes = ByteArray(QuayPassConfig.AVATAR_BLOCK_BYTES).also { pb.get(it) }
        val avatar = QuayPassAvatarCodec.decode(avatarBytes)

        val textLen = pb.short.toInt() and 0xFFFF
        if (textLen > QuayPassConfig.MAX_TEXT_BYTES) {
            return DecodeResult.Failure(DecodeResult.Reason.TEXT_OVERSIZED)
        }
        if (pb.remaining() < textLen) {
            return DecodeResult.Failure(DecodeResult.Reason.TRUNCATED)
        }
        val textBytes = ByteArray(textLen).also { pb.get(it) }

        val text = try {
            decodeText(textBytes)
        } catch (e: TextDecodeException) {
            return DecodeResult.Failure(e.reason)
        }

        return DecodeResult.Success(
            InboundProfile(
                protocolMajor = major,
                protocolMinor = minor,
                nonce = nonce,
                timestamp = timestampInstant,
                avatar = avatar,
                avatarBytes = avatarBytes,
                username = text.username,
                displayName = text.displayName,
                greeting = text.greeting,
                lastGameTitle = text.lastGameTitle,
                lastGamePlatform = text.lastGamePlatform,
                lastGamePlaytimeMinutes = text.lastGamePlaytimeMinutes,
                lastGameIgdbId = text.lastGameIgdbId,
                credentialBundle = credential,
                credentialFingerprint = fingerprintOf(credential.pubkey)
            )
        )
    }

    fun fingerprintOf(pubkey: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256").digest(pubkey)
        return md.take(16).joinToString("") { "%02x".format(it) }
    }

    private data class DecodedText(
        val username: String,
        val displayName: String?,
        val greeting: String?,
        val lastGameTitle: String?,
        val lastGamePlatform: String?,
        val lastGamePlaytimeMinutes: Int?,
        val lastGameIgdbId: Long?
    )

    private class TextDecodeException(val reason: DecodeResult.Reason) : RuntimeException()

    private fun encodeText(profile: OutboundProfile): ByteArray {
        val map = CBORObject.NewMap().apply {
            Add(KEY_USERNAME, sanitize(profile.username, QuayPassConfig.MAX_USERNAME_BYTES))
            profile.displayName?.let { Add(KEY_DISPLAY_NAME, sanitize(it, QuayPassConfig.MAX_DISPLAY_NAME_BYTES)) }
            profile.greeting?.let { Add(KEY_GREETING, sanitize(it, QuayPassConfig.MAX_GREETING_BYTES)) }
            profile.lastGameTitle?.let { Add(KEY_GAME_TITLE, sanitize(it, QuayPassConfig.MAX_GAME_TITLE_BYTES)) }
            profile.lastGamePlatform?.let { Add(KEY_GAME_PLATFORM, sanitize(it, QuayPassConfig.MAX_PLATFORM_SLUG_BYTES)) }
            profile.lastGamePlaytimeMinutes?.let { Add(KEY_GAME_MINUTES, it) }
            profile.lastGameIgdbId?.let { Add(KEY_GAME_IGDB_ID, it) }
        }
        return map.EncodeToBytes()
    }

    private fun decodeText(bytes: ByteArray): DecodedText {
        val obj = try {
            CBORObject.DecodeFromBytes(bytes)
        } catch (_: Throwable) {
            throw TextDecodeException(DecodeResult.Reason.TEXT_PARSE_FAILED)
        }
        if (obj.type != com.upokecenter.cbor.CBORType.Map) {
            throw TextDecodeException(DecodeResult.Reason.TEXT_PARSE_FAILED)
        }

        val username = obj.optString(KEY_USERNAME)
            ?.takeIf { it.isNotEmpty() && it.length <= QuayPassConfig.MAX_USERNAME_BYTES }
            ?: throw TextDecodeException(DecodeResult.Reason.REQUIRED_FIELD_MISSING)

        return DecodedText(
            username = username,
            displayName = obj.optStringChecked(KEY_DISPLAY_NAME, QuayPassConfig.MAX_DISPLAY_NAME_BYTES),
            greeting = obj.optStringChecked(KEY_GREETING, QuayPassConfig.MAX_GREETING_BYTES),
            lastGameTitle = obj.optStringChecked(KEY_GAME_TITLE, QuayPassConfig.MAX_GAME_TITLE_BYTES),
            lastGamePlatform = obj.optStringChecked(KEY_GAME_PLATFORM, QuayPassConfig.MAX_PLATFORM_SLUG_BYTES),
            lastGamePlaytimeMinutes = obj.optIntPositive(KEY_GAME_MINUTES),
            lastGameIgdbId = obj.optLongPositive(KEY_GAME_IGDB_ID)
        )
    }

    private fun sanitize(value: String, maxBytes: Int): String {
        val cleaned = value
            .replace(Regex("[ -]"), "")
            .replace(Regex("[​-‍⁠﻿]"), "")
            .replace(Regex("[‪-‮⁦-⁩]"), "")
        val trimmed = cleaned.trim()
        return trimmed.toByteArray(Charsets.UTF_8).let {
            if (it.size <= maxBytes) trimmed else String(it.copyOfRange(0, maxBytes), Charsets.UTF_8)
        }
    }

    private fun CBORObject.optString(key: String): String? =
        if (ContainsKey(key) && this[key].type == com.upokecenter.cbor.CBORType.TextString) {
            this[key].AsString()
        } else null

    private fun CBORObject.optStringChecked(key: String, maxBytes: Int): String? {
        val raw = optString(key) ?: return null
        if (raw.toByteArray(Charsets.UTF_8).size > maxBytes) {
            throw TextDecodeException(DecodeResult.Reason.TEXT_FIELD_TOO_LONG)
        }
        return sanitize(raw, maxBytes).takeIf { it.isNotEmpty() }
    }

    private fun CBORObject.optIntPositive(key: String): Int? {
        if (!ContainsKey(key)) return null
        val v = this[key]
        if (v.type != com.upokecenter.cbor.CBORType.Integer) return null
        return v.AsInt32Value().takeIf { it >= 0 }
    }

    private fun CBORObject.optLongPositive(key: String): Long? {
        if (!ContainsKey(key)) return null
        val v = this[key]
        if (v.type != com.upokecenter.cbor.CBORType.Integer) return null
        return v.AsInt64Value().takeIf { it >= 0 }
    }

    private const val KEY_USERNAME = "u"
    private const val KEY_DISPLAY_NAME = "dn"
    private const val KEY_GREETING = "g"
    private const val KEY_GAME_TITLE = "gt"
    private const val KEY_GAME_PLATFORM = "gp"
    private const val KEY_GAME_MINUTES = "gm"
    private const val KEY_GAME_IGDB_ID = "gi"

    private const val MIN_VALID_BYTES =
        2 + (1 + 1 + QuayPassConfig.NONCE_BYTES + QuayPassConfig.TIMESTAMP_BYTES +
            QuayPassConfig.AVATAR_BLOCK_BYTES + 2) +
            2 + 32 +
            QuayPassConfig.SIGNATURE_BYTES
}
