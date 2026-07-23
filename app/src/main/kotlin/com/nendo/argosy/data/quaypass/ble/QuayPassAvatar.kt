package com.nendo.argosy.data.quaypass.ble

import java.nio.ByteBuffer

data class QuayPassAvatar(
    val avatarFormatVersion: Int = 1,
    val faceShape: Int = 0,
    val skinColor: Int = 0,
    val wrinkles: Int = 0,
    val makeup: Int = 0,
    val hairType: Int = 0,
    val hairColor: Int = 0,
    val flipHair: Boolean = false,
    val eyeType: Int = 0,
    val eyeColor: Int = 0,
    val eyeScale: Int = 0,
    val eyeVertStretch: Int = 0,
    val eyeRotation: Int = 0,
    val eyeSpacing: Int = 0,
    val eyeYPosition: Int = 0,
    val eyebrowType: Int = 0,
    val eyebrowColor: Int = 0,
    val eyebrowScale: Int = 0,
    val eyebrowVertStretch: Int = 0,
    val eyebrowRotation: Int = 0,
    val eyebrowSpacing: Int = 0,
    val eyebrowYPosition: Int = 0,
    val noseType: Int = 0,
    val noseScale: Int = 0,
    val noseYPosition: Int = 0,
    val mouthType: Int = 0,
    val mouthColor: Int = 0,
    val mouthScale: Int = 0,
    val mouthHorizStretch: Int = 0,
    val mouthYPosition: Int = 0,
    val mustacheType: Int = 0,
    val goateeType: Int = 0,
    val facialHairColor: Int = 0,
    val mustacheScale: Int = 0,
    val mustacheYPosition: Int = 0,
    val glassesType: Int = 0,
    val glassesColor: Int = 0,
    val glassesScale: Int = 0,
    val glassesYPosition: Int = 0,
    val hatType: Int = 0,
    val hatColor: Int = 0,
    val moleEnabled: Boolean = false,
    val moleScale: Int = 0,
    val moleXPosition: Int = 0,
    val moleYPosition: Int = 0,
    val favoriteColor: Int = 0
)

/** Bit-packs the avatar struct into a fixed-size byte block. */
object QuayPassAvatarCodec {

    fun encode(avatar: QuayPassAvatar): ByteArray {
        val w = BitWriter(QuayPassConfig.AVATAR_BLOCK_BYTES)
        w.writeBits(avatar.avatarFormatVersion, 8)
        w.writeBits(avatar.faceShape, 4)
        w.writeBits(avatar.skinColor, 4)
        w.writeBits(avatar.wrinkles, 4)
        w.writeBits(avatar.makeup, 4)
        w.writeBits(avatar.hairType, 8)
        w.writeBits(avatar.hairColor, 4)
        w.writeBits(if (avatar.flipHair) 1 else 0, 1)
        w.writeBits(avatar.eyeType, 6)
        w.writeBits(avatar.eyeColor, 4)
        w.writeBits(avatar.eyeScale, 4)
        w.writeBits(avatar.eyeVertStretch, 3)
        w.writeBits(avatar.eyeRotation, 5)
        w.writeBits(avatar.eyeSpacing, 4)
        w.writeBits(avatar.eyeYPosition, 5)
        w.writeBits(avatar.eyebrowType, 5)
        w.writeBits(avatar.eyebrowColor, 4)
        w.writeBits(avatar.eyebrowScale, 4)
        w.writeBits(avatar.eyebrowVertStretch, 3)
        w.writeBits(avatar.eyebrowRotation, 4)
        w.writeBits(avatar.eyebrowSpacing, 4)
        w.writeBits(avatar.eyebrowYPosition, 5)
        w.writeBits(avatar.noseType, 5)
        w.writeBits(avatar.noseScale, 4)
        w.writeBits(avatar.noseYPosition, 5)
        w.writeBits(avatar.mouthType, 6)
        w.writeBits(avatar.mouthColor, 4)
        w.writeBits(avatar.mouthScale, 4)
        w.writeBits(avatar.mouthHorizStretch, 3)
        w.writeBits(avatar.mouthYPosition, 5)
        w.writeBits(avatar.mustacheType, 3)
        w.writeBits(avatar.goateeType, 3)
        w.writeBits(avatar.facialHairColor, 4)
        w.writeBits(avatar.mustacheScale, 4)
        w.writeBits(avatar.mustacheYPosition, 5)
        w.writeBits(avatar.glassesType, 5)
        w.writeBits(avatar.glassesColor, 4)
        w.writeBits(avatar.glassesScale, 4)
        w.writeBits(avatar.glassesYPosition, 5)
        w.writeBits(avatar.hatType, 4)
        w.writeBits(avatar.hatColor, 4)
        w.writeBits(if (avatar.moleEnabled) 1 else 0, 1)
        w.writeBits(avatar.moleScale, 4)
        w.writeBits(avatar.moleXPosition, 5)
        w.writeBits(avatar.moleYPosition, 5)
        w.writeBits(avatar.favoriteColor, 4)
        return w.finish()
    }

    fun decode(bytes: ByteArray): QuayPassAvatar {
        if (bytes.size < QuayPassConfig.AVATAR_BLOCK_BYTES) {
            return QuayPassAvatar()
        }
        val r = BitReader(bytes)
        return QuayPassAvatar(
            avatarFormatVersion = r.readBits(8),
            faceShape = r.readBits(4),
            skinColor = r.readBits(4),
            wrinkles = r.readBits(4),
            makeup = r.readBits(4),
            hairType = r.readBits(8),
            hairColor = r.readBits(4),
            flipHair = r.readBits(1) == 1,
            eyeType = r.readBits(6),
            eyeColor = r.readBits(4),
            eyeScale = r.readBits(4),
            eyeVertStretch = r.readBits(3),
            eyeRotation = r.readBits(5),
            eyeSpacing = r.readBits(4),
            eyeYPosition = r.readBits(5),
            eyebrowType = r.readBits(5),
            eyebrowColor = r.readBits(4),
            eyebrowScale = r.readBits(4),
            eyebrowVertStretch = r.readBits(3),
            eyebrowRotation = r.readBits(4),
            eyebrowSpacing = r.readBits(4),
            eyebrowYPosition = r.readBits(5),
            noseType = r.readBits(5),
            noseScale = r.readBits(4),
            noseYPosition = r.readBits(5),
            mouthType = r.readBits(6),
            mouthColor = r.readBits(4),
            mouthScale = r.readBits(4),
            mouthHorizStretch = r.readBits(3),
            mouthYPosition = r.readBits(5),
            mustacheType = r.readBits(3),
            goateeType = r.readBits(3),
            facialHairColor = r.readBits(4),
            mustacheScale = r.readBits(4),
            mustacheYPosition = r.readBits(5),
            glassesType = r.readBits(5),
            glassesColor = r.readBits(4),
            glassesScale = r.readBits(4),
            glassesYPosition = r.readBits(5),
            hatType = r.readBits(4),
            hatColor = r.readBits(4),
            moleEnabled = r.readBits(1) == 1,
            moleScale = r.readBits(4),
            moleXPosition = r.readBits(5),
            moleYPosition = r.readBits(5),
            favoriteColor = r.readBits(4)
        )
    }
}

private class BitWriter(sizeBytes: Int) {
    private val out = ByteArray(sizeBytes)
    private var bitOffset = 0

    fun writeBits(value: Int, bits: Int) {
        require(bits in 1..32)
        val masked = value and ((1L shl bits) - 1).toInt()
        var remaining = bits
        var v = masked
        while (remaining > 0) {
            val byteIndex = bitOffset ushr 3
            if (byteIndex >= out.size) return
            val bitInByte = bitOffset and 0x07
            val freeInByte = 8 - bitInByte
            val take = minOf(remaining, freeInByte)
            val highBitsToTake = remaining - take
            val chunk = (v ushr highBitsToTake) and ((1 shl take) - 1)
            val shift = freeInByte - take
            out[byteIndex] = (out[byteIndex].toInt() or (chunk shl shift)).toByte()
            bitOffset += take
            remaining -= take
            v = v and ((1 shl highBitsToTake) - 1)
        }
    }

    fun finish(): ByteArray = out
}

private class BitReader(private val src: ByteArray) {
    private var bitOffset = 0

    fun readBits(bits: Int): Int {
        require(bits in 1..32)
        var remaining = bits
        var result = 0
        while (remaining > 0) {
            val byteIndex = bitOffset ushr 3
            if (byteIndex >= src.size) return result
            val bitInByte = bitOffset and 0x07
            val available = 8 - bitInByte
            val take = minOf(remaining, available)
            val shift = available - take
            val byte = src[byteIndex].toInt() and 0xFF
            val chunk = (byte ushr shift) and ((1 shl take) - 1)
            result = (result shl take) or chunk
            bitOffset += take
            remaining -= take
        }
        return result
    }
}

/** Helper for the v1 PoC: avatar with just a favoriteColor index, all other slots zero. */
fun colorOnlyAvatar(favoriteColor: Int): QuayPassAvatar =
    QuayPassAvatar(favoriteColor = favoriteColor and 0x0F)

internal fun ByteBuffer.putShortBE(value: Int): ByteBuffer = putShort(value.toShort())
