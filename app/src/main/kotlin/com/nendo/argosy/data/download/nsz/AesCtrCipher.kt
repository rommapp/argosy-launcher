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

    init {
        require(key.size == 16) { "AES-128-CTR requires 16-byte key" }
        require(counter.size == 16) { "CTR counter must be 16 bytes" }

        val iv = counter.copyOf()
        writeBlockIndexBigEndian(iv, initialOffset / 16)

        cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(iv)
            )
        }

        val misalignment = (initialOffset % 16).toInt()
        if (misalignment > 0) {
            cipher.update(ByteArray(misalignment))
        }
    }

    fun process(data: ByteArray): ByteArray = cipher.update(data)

    fun process(data: ByteArray, offset: Int, length: Int): ByteArray =
        cipher.update(data, offset, length)

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
