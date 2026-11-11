package com.nendo.argosy.data.download.transfer

import com.nendo.argosy.data.download.DownloadFailureReason
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class TransferIntegrityTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var api: ResumableTransferTest.TestApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient()).build().create(ResumableTransferTest.TestApi::class.java)
    }

    @After fun tearDown() { server.shutdown() }

    private fun digest(bytes: ByteArray, algorithm: String = "SHA-256"): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm).digest(bytes))

    private fun zipBytes(): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("fixture.txt"))
            zip.write("locally generated archive fixture".toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    @Test fun syntheticPngCartridgeAcceptsNormalizedMime() = runBlocking {
        val bytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAKAAAADNCAYAAAA/pXupAAAAlklEQVR4nO3BMQEAAADCoPVPbQlPoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAC4GwFrAAGW0tlGAAAAAElFTkSuQmCC"
        )
        val file = temporary.newFile("synthetic.p8.png")
        var checkpoint = TransferCheckpoint()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("Content-Type", "IMAGE/PNG; charset=binary"))
        assertEquals(TransferResult.Verified(bytes.size.toLong()), ResumableTransfer().attempt(
            file, checkpoint, api::download, { checkpoint = it }, { _, _ -> }))
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), checkpoint.validatedBytes)
    }

    @Test fun ordinaryRomAcceptsImageMimeTypes() = runBlocking {
        val bytes = ByteArray(2048) { (it % 251).toByte() }
        for (mime in listOf("image/png", "image/jpeg", "IMAGE/WEBP; charset=binary")) {
            val file = temporary.newFile()
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("Content-Type", mime))
            val result = ResumableTransfer().attempt(file, TransferCheckpoint(), api::download,
                {}, { _, _ -> })
            assertEquals(TransferResult.Verified(bytes.size.toLong()), result)
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun resumedPngAcceptsFullAndPartialResponses() = runBlocking {
        for (partial in listOf(false, true)) {
            val prefix = ByteArray(1024) { 42 }
            val file = temporary.newFile().apply { writeBytes(prefix) }
            var checkpoint = TransferCheckpoint("\"fixture\"", 3072)
            val response = MockResponse().setBody(Buffer().write(ByteArray(if (partial) 2048 else 3072)))
                .setHeader("Content-Type", "image/png").setHeader("ETag", "\"fixture\"")
            if (partial) response.setResponseCode(206).setHeader("Content-Range", "bytes 1024-3071/3072")
            server.enqueue(response)
            val result = ResumableTransfer().attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> })
            assertEquals(TransferResult.Verified(3072), result)
            assertArrayEquals(if (partial) prefix + ByteArray(2048) else ByteArray(3072), file.readBytes())
            assertEquals(3072L, checkpoint.validatedBytes)
        }
    }

    @Test fun requiredZipRejectsNonZipDespiteImageMime() = runBlocking {
        val file = temporary.newFile("cart.p8.png")
        var checkpoint = TransferCheckpoint()
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(2048))).setHeader("Content-Type", "image/png"))
        val result = ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }, requireZip = true)
        assertEquals(DownloadFailureReason.IntegrityCheckFailed,
            (result as TransferResult.Permanent).reason)
        assertNull(checkpoint.validatedBytes)
    }

    @Test fun interruptedInitialChecksumsSurviveSerializedRestartAndMissingFinalHeaders() = runBlocking {
        val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
        for ((header, value) in listOf(
            "Content-MD5" to digest(bytes, "MD5"),
            "Content-Digest" to "sha-256=:${digest(bytes)}:",
            "Digest" to "sha-256=${digest(bytes)}",
            "Repr-Digest" to "sha-256=:${digest(bytes)}:"
        )) {
            for (corrupt in listOf(false, true)) {
                val file = temporary.newFile()
                val persisted = temporary.newFile()
                var checkpoint = TransferCheckpoint()
                server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("ETag", "\"fixture\"")
                    .setHeader(header, value))
                val interrupted = ResumableTransfer().attempt(file, checkpoint, api::download,
                    { checkpoint = it }, { _, _ -> }, afterWrite = { throw IOException("synthetic interruption") })
                assertTrue(interrupted is TransferResult.Retryable)
                assertNotNull(checkpoint.representationChecksum)
                assertNull(checkpoint.validatedBytes)
                persisted.writeText(checkpoint.representationChecksum!!)
                checkpoint = TransferCheckpoint(checkpoint.validator, checkpoint.totalBytes,
                    representationChecksum = persisted.readText())
                val offset = file.length()
                assertTrue(offset in 1 until bytes.size.toLong())
                val suffix = bytes.copyOfRange(offset.toInt(), bytes.size)
                if (corrupt) suffix[0] = (suffix[0].toInt() xor 1).toByte()
                server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(suffix))
                    .setHeader("ETag", "\"fixture\"")
                    .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}"))
                val result = ResumableTransfer().attempt(file, checkpoint, api::download,
                    { checkpoint = it }, { _, _ -> })
                if (corrupt) {
                    assertTrue(result is TransferResult.Permanent)
                    assertEquals(offset, file.length())
                    assertNull(checkpoint.validatedBytes)
                } else {
                    assertEquals(TransferResult.Verified(bytes.size.toLong()), result)
                    assertArrayEquals(bytes, file.readBytes())
                }
                assertEquals(persisted.readText(), checkpoint.representationChecksum)
            }
        }
    }

    @Test fun intermediateRepresentationChecksumIsRetainedAndConflictsPreservePrefix() = runBlocking {
        val bytes = "abcdef".toByteArray()
        val file = temporary.newFile().apply { writeText("abc") }
        var checkpoint = TransferCheckpoint("\"fixture\"", 6)
        server.enqueue(MockResponse().setResponseCode(206).setBody("de").setHeader("ETag", "\"fixture\"")
            .setHeader("Content-Range", "bytes 3-4/6").setHeader("Repr-Digest", "sha-256=:${digest(bytes)}:"))
        assertEquals(TransferResult.Continue, ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }))
        assertNotNull(checkpoint.representationChecksum)
        assertNull(checkpoint.validatedBytes)
        server.enqueue(MockResponse().setResponseCode(206).setBody("f").setHeader("ETag", "\"fixture\"")
            .setHeader("Content-Range", "bytes 5-5/6").setHeader("Digest", "sha-256=${digest("wrong".toByteArray())}"))
        assertTrue(ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }) is TransferResult.Permanent)
        assertEquals("abcde", file.readText())
        server.enqueue(MockResponse().setResponseCode(206).setBody("f").setHeader("ETag", "\"fixture\"")
            .setHeader("Content-Range", "bytes 5-5/6"))
        assertEquals(TransferResult.Verified(6), ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }))
    }

    @Test fun replacementClearsOldChecksumBeforeReset() = runBlocking {
        val file = temporary.newFile().apply { writeText("old") }
        val snapshots = mutableListOf<Pair<TransferCheckpoint, String>>()
        server.enqueue(MockResponse().setBody("new").setHeader("ETag", "\"new\""))
        val checkpoint = TransferCheckpoint("\"old\"", 3, representationChecksum = "sha-256=${digest("old".toByteArray())}")
        assertEquals(TransferResult.Verified(3), ResumableTransfer().attempt(file, checkpoint, api::download,
            { snapshots += it to file.readText() }, { _, _ -> }))
        assertEquals(TransferCheckpoint() to "old", snapshots.first())
        assertTrue(snapshots.all { it.first.representationChecksum == null })
    }

    @Test fun cleanFramedCorruptZipNeverGetsCompletionMarker() = runBlocking {
        val valid = zipBytes()
        for (bytes in listOf(valid.copyOf(valid.size - 10), "not an archive".toByteArray())) {
            val file = temporary.newFile()
            var checkpoint = TransferCheckpoint()
            server.enqueue(MockResponse().setChunkedBody(Buffer().write(bytes), 8))
            val result = ResumableTransfer().attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }, requireZip = true)
            assertTrue(result is TransferResult.Permanent)
            assertNull(checkpoint.validatedBytes)
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun zipStructureIsCheckedOnlyAtCompleteRepresentation() = runBlocking {
        val bytes = zipBytes()
        val file = temporary.newFile().apply { writeBytes(bytes.copyOf(10)) }
        var checkpoint = TransferCheckpoint("\"fixture\"", bytes.size.toLong())
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(bytes, 10, 10))
            .setHeader("ETag", "\"fixture\"").setHeader("Content-Range", "bytes 10-19/${bytes.size}"))
        assertEquals(TransferResult.Continue, ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }, requireZip = true))
        assertNull(checkpoint.validatedBytes)
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(bytes, 20, bytes.size - 20))
            .setHeader("ETag", "\"fixture\"").setHeader("Content-Range", "bytes 20-${bytes.lastIndex}/${bytes.size}"))
        assertEquals(TransferResult.Verified(bytes.size.toLong()), ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }, requireZip = true))
        assertEquals(bytes.size.toLong(), checkpoint.validatedBytes)
        assertArrayEquals(bytes, file.readBytes())
    }
}
