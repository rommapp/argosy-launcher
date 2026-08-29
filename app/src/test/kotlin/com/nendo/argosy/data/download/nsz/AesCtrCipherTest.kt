package com.nendo.argosy.data.download.nsz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun `block index lands in the low half of the counter`() {
        val counter = ByteArray(16) { 0x11 }
        AesCtrCipher.writeBlockIndexBigEndian(counter, 0x0102030405060708L)

        assertArrayEquals(
            "nsz keeps the section nonce in the high half",
            ByteArray(8) { 0x11 },
            counter.copyOfRange(0, 8)
        )
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            counter.copyOfRange(8, 16)
        )
    }

    @Test
    fun `block index replaces rather than adds to the stored counter`() {
        val counter = ByteArray(16) { 0 }
        counter[15] = 0xFF.toByte()
        AesCtrCipher.writeBlockIndexBigEndian(counter, 1)

        assertEquals(0.toByte(), counter[14])
        assertEquals(1.toByte(), counter[15])
    }

    @Test
    fun `cipher resumed at an offset matches the continuous keystream`() {
        val key = ByteArray(16) { (it * 7 + 1).toByte() }
        val counter = ByteArray(16) { if (it < 8) (it + 1).toByte() else 0 }
        val data = ByteArray(512) { (it % 251).toByte() }

        val expected = AesCtrCipher(key, counter, 0).process(data)

        for (split in listOf(16, 0x25, 256)) {
            val head = AesCtrCipher(key, counter, 0).process(data, 0, split)
            val tail = AesCtrCipher(key, counter, split.toLong())
                .process(data, split, data.size - split)
            assertArrayEquals(
                "Resuming at offset $split must not shift the keystream",
                expected,
                head + tail
            )
        }
    }
}
