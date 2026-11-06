package com.nendo.argosy.data.download.nsz

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming AES-128-CTR cipher for NCZ section re-encryption.
 *
 * NCZ sections with a CTR crypto type store their data as plaintext
 * after zstd decompression. The original NCA had these sections encrypted
 * with AES-128-CTR. We must re-encrypt them to produce a valid NCA.
 *
 * nsz keeps the section nonce in the counter's first 8 bytes and leaves the
 * low 8 for the block index. [initialOffset] is absolute inside the
 * decompressed NCA and need not be 16-byte aligned.
 */
class AesCtrCipher(
    key: ByteArray,
    counter: ByteArray,
    initialOffset: Long
) {
    private val cipher: Cipher
    private val keySpec: SecretKeySpec
    private val nonce: ByteArray
    private var position = initialOffset

    init {
        require(key.size == 16) { "AES-128-CTR requires 16-byte key" }
        require(counter.size == 16) { "CTR counter must be 16 bytes" }

        keySpec = SecretKeySpec(key, "AES")
        nonce = counter.copyOf()
        cipher = Cipher.getInstance("AES/CTR/NoPadding")
    }

    fun process(data: ByteArray): ByteArray = process(data, 0, data.size)

    fun process(data: ByteArray, offset: Int, length: Int): ByteArray {
        require(offset >= 0 && length >= 0 && offset <= data.size - length) {
            "Invalid input slice"
        }
        if (length == 0) return ByteArray(0)

        val iv = nonce.copyOf()
        writeBlockIndexBigEndian(iv, position / 16)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(iv))

        val prefixSize = (position % 16).toInt()
        val output = if (prefixSize == 0) {
            cipher.doFinal(data, offset, length)
        } else {
            val input = ByteArray(prefixSize + length)
            data.copyInto(input, prefixSize, offset, offset + length)
            cipher.doFinal(input).copyOfRange(prefixSize, input.size)
        }
        position += length
        return output
    }

    companion object {
        private const val NONCE_SIZE = 8

        internal fun writeBlockIndexBigEndian(
            counter: ByteArray,
            blockIndex: Long
        ) {
            ByteBuffer.wrap(counter, NONCE_SIZE, NONCE_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(blockIndex)
        }
    }
}
