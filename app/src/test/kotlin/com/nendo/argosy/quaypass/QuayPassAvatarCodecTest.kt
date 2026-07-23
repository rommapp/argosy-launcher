package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec
import com.nendo.argosy.data.quaypass.ble.QuayPassConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class QuayPassAvatarCodecTest {

    @Test
    fun `encodes to the fixed block size`() {
        val bytes = QuayPassAvatarCodec.encode(QuayPassAvatar())
        assertEquals(QuayPassConfig.AVATAR_BLOCK_BYTES, bytes.size)
    }

    @Test
    fun `round trips a populated avatar`() {
        val avatar = QuayPassAvatar(
            faceShape = 5,
            skinColor = 3,
            hairType = 100,
            hairColor = 6,
            flipHair = true,
            eyeType = 40,
            eyeColor = 4,
            eyebrowType = 12,
            noseType = 9,
            mouthType = 20,
            mustacheType = 2,
            goateeType = 1,
            glassesType = 7,
            hatType = 4,
            moleEnabled = true,
            favoriteColor = 11
        )
        val decoded = QuayPassAvatarCodec.decode(QuayPassAvatarCodec.encode(avatar))
        assertEquals(avatar, decoded)
    }

    @Test
    fun `decodes any 32 bytes without throwing`() {
        for (seed in 0 until 256) {
            val bytes = ByteArray(QuayPassConfig.AVATAR_BLOCK_BYTES) { (it * seed + seed).toByte() }
            QuayPassAvatarCodec.decode(bytes)
        }
    }
}
