package com.nendo.argosy.data.download.nsz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(Parameterized::class)
class AesCtrCipherTest(private val sliced: Boolean) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "sliced={0}")
        fun overloads(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
    }

    private fun process(cipher: AesCtrCipher, data: ByteArray): ByteArray =
        if (sliced) cipher.process(byteArrayOf(99) + data + byteArrayOf(98), 1, data.size)
        else cipher.process(data)

    @Test
    fun `buffering provider returns null then whole blocks and flushes tail`() {
        BufferingCtrProvider.withProvider { provider ->
            val cipher = Cipher.getInstance(BufferingCtrProvider.TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(16), "AES"), IvParameterSpec(ByteArray(16)))
            assertEquals(provider, cipher.provider)
            assertNull(cipher.update(ByteArray(3)))
            val block = cipher.update(ByteArray(16))
            val tail = cipher.doFinal()
            val baseline = Cipher.getInstance(BufferingCtrProvider.TRANSFORMATION, provider.baseline)
            baseline.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(16), "AES"), IvParameterSpec(ByteArray(16)))
            assertEquals(16, block.size)
            assertEquals(3, tail.size)
            assertArrayEquals(baseline.doFinal(ByteArray(19)), block + tail)
        }
    }

    @Test
    fun `short input returns ciphertext when provider update returns null`() {
        assertBufferedOutput(0, listOf(3))
    }

    @Test
    fun `unaligned input returns every byte without truncation`() {
        assertBufferedOutput(0, listOf(19))
    }

    @Test
    fun `sequential short chunks preserve the keystream`() {
        assertBufferedOutput(0, listOf(3, 5, 11, 1, 17))
    }

    @Test
    fun `misaligned initial offset discards only the prefix keystream`() {
        assertBufferedOutput(5, listOf(16, 3, 19))
    }

    @Test
    fun `empty input is a no-op before and between chunks`() {
        assertBufferedOutput(0, listOf(0, 16, 0, 16, 0))
    }

    @Test
    fun `invalid slices do not advance the stream position`() {
        BufferingCtrProvider.withProvider { provider ->
            val data = ByteArray(32) { it.toByte() }
            val baseline = Cipher.getInstance(BufferingCtrProvider.TRANSFORMATION, provider.baseline)
            baseline.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(16), "AES"), IvParameterSpec(ByteArray(16)))
            val expected = baseline.doFinal(data)
            val cipher = AesCtrCipher(ByteArray(16), ByteArray(16), 0)
            val first = process(cipher, data.copyOfRange(0, 3))
            for ((offset, length) in listOf(-1 to 1, 0 to -1, 33 to 0, 31 to 2, 1 to Int.MAX_VALUE)) {
                val error = runCatching { cipher.process(data, offset, length) }.exceptionOrNull()
                assertTrue("invalid slice ($offset, $length): $error", error is IllegalArgumentException)
            }
            val rest = process(cipher, data.copyOfRange(3, data.size))
            assertArrayEquals(expected, first + rest)
        }
    }

    private fun assertBufferedOutput(initialOffset: Int, sizes: List<Int>) {
        BufferingCtrProvider.withProvider { provider ->
            val data = ByteArray(sizes.sum()) { (it * 7 + 3).toByte() }
            val baseline = Cipher.getInstance(BufferingCtrProvider.TRANSFORMATION, provider.baseline)
            baseline.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(16), "AES"), IvParameterSpec(ByteArray(16)))
            val expected = baseline.doFinal(ByteArray(initialOffset) + data).drop(initialOffset).toByteArray()
            val cipher = AesCtrCipher(ByteArray(16), ByteArray(16), initialOffset.toLong())
            var offset = 0
            for (size in sizes) {
                val actual = process(cipher, data.copyOfRange(offset, offset + size))
                assertEquals("output length at stream offset $offset", size, actual.size)
                assertArrayEquals(expected.copyOfRange(offset, offset + size), actual)
                offset += size
            }
            assertTrue("production cipher must select buffering provider", provider.initializations > 0)
        }
    }

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
