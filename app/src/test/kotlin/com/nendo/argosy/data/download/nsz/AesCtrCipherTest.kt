package com.nendo.argosy.data.download.nsz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AesCtrCipherTest {

    @Test
    fun `CTR encrypt then decrypt produces original plaintext`() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val counter = ByteArray(16) { 0 }
        val plaintext = ByteArray(256) { (it * 3).toByte() }

        val cipher1 = AesCtrCipher(key, counter, 0)
        val encrypted = cipher1.process(plaintext)

        assertFalse(
            "Encrypted data should differ from plaintext",
            plaintext.contentEquals(encrypted)
        )

        val cipher2 = AesCtrCipher(key, counter, 0)
        val decrypted = cipher2.process(encrypted)

        assertArrayEquals(
            "Round-trip should recover original plaintext",
            plaintext,
            decrypted
        )
    }

    @Test
    fun `CTR with non-zero initial offset produces different output`() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val counter = ByteArray(16) { 0 }
        val data = ByteArray(64) { 0x42 }

        val cipher0 = AesCtrCipher(key, counter, 0)
        val result0 = cipher0.process(data)

        val cipher1 = AesCtrCipher(key, counter, 0x4000)
        val result1 = cipher1.process(data)

        assertFalse(
            "Different offsets should produce different ciphertext",
            result0.contentEquals(result1)
        )
    }

    @Test
    fun `streaming process matches single-shot process`() {
        val key = ByteArray(16) { (it + 5).toByte() }
        val counter = ByteArray(16) { 0 }
        val data = ByteArray(128) { it.toByte() }

        val cipherFull = AesCtrCipher(key, counter, 0)
        val fullResult = cipherFull.process(data)

        val cipherChunked = AesCtrCipher(key, counter, 0)
        val chunk1 = cipherChunked.process(data, 0, 48)
        val chunk2 = cipherChunked.process(data, 48, 80)

        val chunkedResult = chunk1 + chunk2
        assertArrayEquals(
            "Chunked processing should match single-shot",
            fullResult,
            chunkedResult
        )
    }

    @Test
    fun `addCounterBigEndian adds value correctly`() {
        val counter = ByteArray(16) { 0 }
        AesCtrCipher.addCounterBigEndian(counter, 0x100)
        assert(counter[6] == 1.toByte())
        assert(counter[7] == 0.toByte())
    }

    @Test
    fun `addCounterBigEndian handles carry`() {
        val counter = ByteArray(16) { 0 }
        counter[7] = 0xFF.toByte()
        AesCtrCipher.addCounterBigEndian(counter, 1)
        assert(counter[6] == 1.toByte())
        assert(counter[7] == 0.toByte())
    }
}
