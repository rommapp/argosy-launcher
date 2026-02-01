package com.nendo.argosy.util

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-XTS implementation for Nintendo Switch NCA headers.
 *
 * Nintendo uses a specific variant:
 * - 0x200-byte (512) sectors
 * - Big-endian sector numbers as tweaks
 * - Standard GF(2^128) multiplication for tweak updates
 */
object AesXts {

    private const val SECTOR_SIZE = 0x200
    private const val BLOCK_SIZE = 16

    fun decrypt(data: ByteArray, key: ByteArray, startSector: Long = 0): ByteArray {
        require(key.size == 32) { "XTS requires 32-byte key" }

        val key1 = key.copyOfRange(0, 16)
        val key2 = key.copyOfRange(16, 32)

        val dataCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key1, "AES"))
        }
        val tweakCipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key2, "AES"))
        }

        val result = ByteArray(data.size)
        val numSectors = data.size / SECTOR_SIZE

        for (sectorIdx in 0 until numSectors) {
            val sectorStart = sectorIdx * SECTOR_SIZE
            val sectorNum = startSector + sectorIdx

            // Nintendo uses big-endian sector numbers
            var tweak = computeTweak(tweakCipher, sectorNum)

            for (blockOffset in 0 until SECTOR_SIZE step BLOCK_SIZE) {
                val offset = sectorStart + blockOffset
                if (offset + BLOCK_SIZE <= data.size) {
                    val block = data.copyOfRange(offset, offset + BLOCK_SIZE)
                    xorBlocks(block, tweak)
                    val decrypted = dataCipher.doFinal(block)
                    xorBlocks(decrypted, tweak)
                    System.arraycopy(decrypted, 0, result, offset, BLOCK_SIZE)
                    tweak = multiplyTweakByX(tweak)
                }
            }
        }

        // Handle remaining data (partial sector)
        val remainingStart = numSectors * SECTOR_SIZE
        if (remainingStart < data.size) {
            var tweak = computeTweak(tweakCipher, startSector + numSectors)
            var offset = remainingStart
            while (offset + BLOCK_SIZE <= data.size) {
                val block = data.copyOfRange(offset, offset + BLOCK_SIZE)
                xorBlocks(block, tweak)
                val decrypted = dataCipher.doFinal(block)
                xorBlocks(decrypted, tweak)
                System.arraycopy(decrypted, 0, result, offset, BLOCK_SIZE)
                tweak = multiplyTweakByX(tweak)
                offset += BLOCK_SIZE
            }
        }

        return result
    }

    private fun computeTweak(tweakCipher: Cipher, sectorNumber: Long): ByteArray {
        // Big-endian 16-byte sector number (Nintendo's format)
        val tweakInput = ByteArray(16)
        var num = sectorNumber
        for (i in 15 downTo 8) {
            tweakInput[i] = (num and 0xFF).toByte()
            num = num shr 8
        }
        return tweakCipher.doFinal(tweakInput)
    }

    private fun xorBlocks(a: ByteArray, b: ByteArray) {
        for (i in a.indices) {
            a[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
    }

    private fun multiplyTweakByX(tweak: ByteArray): ByteArray {
        // GF(2^128) multiplication by x - same as CASHEW
        val carry = (tweak[15].toInt() ushr 7) and 1
        val result = ByteArray(16)

        for (i in 15 downTo 1) {
            result[i] = (((tweak[i].toInt() and 0xFF) shl 1) or
                        ((tweak[i - 1].toInt() and 0xFF) ushr 7)).toByte()
        }
        result[0] = ((tweak[0].toInt() and 0xFF) shl 1).toByte()

        if (carry != 0) {
            result[0] = (result[0].toInt() xor 0x87).toByte()
        }
        return result
    }
}
