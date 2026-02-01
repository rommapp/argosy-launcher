package com.nendo.argosy.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object AesXts {

    fun decrypt(data: ByteArray, key: ByteArray, sectorNumber: Long, sectorSize: Int): ByteArray {
        require(key.size == 32) { "XTS requires 32-byte key" }
        require(data.size % 16 == 0) { "Data must be multiple of 16 bytes" }

        val key1 = key.copyOfRange(0, 16)
        val key2 = key.copyOfRange(16, 32)

        val dataCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key1, "AES"))
        }
        val tweakCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key2, "AES"))
        }

        val result = ByteArray(data.size)
        var tweak = computeInitialTweak(tweakCipher, sectorNumber)

        var offset = 0
        while (offset < data.size) {
            val block = data.copyOfRange(offset, offset + 16)
            xorBlocks(block, tweak)
            val decrypted = dataCipher.doFinal(block)
            xorBlocks(decrypted, tweak)
            System.arraycopy(decrypted, 0, result, offset, 16)
            tweak = multiplyTweakByX(tweak)
            offset += 16
        }

        return result
    }

    private fun computeInitialTweak(tweakCipher: Cipher, sectorNumber: Long): ByteArray {
        val tweakInput = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(sectorNumber)
            .putLong(0L)
            .array()
        return tweakCipher.doFinal(tweakInput)
    }

    private fun xorBlocks(a: ByteArray, b: ByteArray) {
        for (i in a.indices) {
            a[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
    }

    private fun multiplyTweakByX(tweak: ByteArray): ByteArray {
        val result = ByteArray(16)
        var carry = 0
        for (i in 0 until 16) {
            val b = tweak[i].toInt() and 0xFF
            result[i] = ((b shl 1) or carry).toByte()
            carry = b shr 7
        }
        if (carry != 0) {
            result[0] = (result[0].toInt() xor 0x87).toByte()
        }
        return result
    }
}
