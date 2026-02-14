package com.nendo.argosy.data.download.nsz

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-128-CTR cipher for NCZ section re-encryption.
 *
 * NCZ sections with cryptoType 3 or 4 store their data as plaintext
 * after zstd decompression. The original NCA had these sections encrypted
 * with AES-128-CTR. We must re-encrypt them to produce a valid NCA.
 *
 * The IV is constructed from the section's 16-byte counter with the
 * initial offset folded into the upper 8 bytes as a big-endian int64.
 */
class AesCtrCipher(
    key: ByteArray,
    counter: ByteArray,
    initialOffset: Long
) {
    private val cipher: Cipher

    init {
        require(key.size == 16) { "AES-128-CTR requires 16-byte key" }
        require(counter.size == 16) { "CTR counter must be 16 bytes" }

        val iv = counter.copyOf()
        val blockNumber = initialOffset / 16
        addCounterBigEndian(iv, blockNumber)

        cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv)
            )
        }
    }

    fun process(data: ByteArray): ByteArray = cipher.update(data)

    fun process(data: ByteArray, offset: Int, length: Int): ByteArray =
        cipher.update(data, offset, length)

    companion object {
        internal fun addCounterBigEndian(
            counter: ByteArray,
            value: Long
        ) {
            var carry = value
            for (i in 7 downTo 0) {
                carry += (counter[i].toLong() and 0xFF)
                counter[i] = (carry and 0xFF).toByte()
                carry = carry ushr 8
            }
        }
    }
}
