package com.nendo.argosy.data.download.transfer

import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit

class SegmentChecksumRecoveryTest {
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

    private fun digest(text: String, algorithm: String = "SHA-256") =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance(algorithm).digest(text.toByteArray()))

    private fun segment(header: String, body: String, expected: String = "def", range: String = "3-5/6") =
        MockResponse().setResponseCode(206).setChunkedBody(body, 1)
            .setHeader("ETag", "\"v1\"").setHeader("Content-Range", "bytes $range")
            .setHeader(header, if (header == "Content-MD5") digest(expected, "MD5") else "sha-256=:${digest(expected)}:")

    @Test fun interruptedSegmentsRollbackBeforeTrustedCheckpointAndRetry() = runBlocking {
        for (header in listOf("Content-MD5", "Content-Digest")) {
            for (failure in listOf("io", "cancel", "short", "mismatch", "overflow")) {
                val file = temporary.newFile().apply { writeText("abc") }
                var checkpoint = TransferCheckpoint("\"v1\"", 6)
                val snapshots = mutableListOf<Pair<TransferCheckpoint, String>>()
                server.enqueue(segment(header, when (failure) {
                    "mismatch" -> "Xef"
                    "overflow" -> "defg"
                    else -> "X"
                }))
                var cancelled = false
                val result = try {
                    ResumableTransfer().attempt(file, checkpoint, api::download, {
                        checkpoint = it
                        snapshots += it to file.readText()
                    }, { _, _ -> }, afterWrite = {
                        assertEquals(TransferCheckpoint(), checkpoint)
                        when (failure) {
                            "io" -> throw IOException("synthetic disconnect")
                            "cancel" -> throw CancellationException("synthetic cancellation")
                        }
                    })
                } catch (_: CancellationException) {
                    cancelled = true
                    null
                }
                assertEquals(failure == "cancel", cancelled)
                if (failure in listOf("io", "short")) assertTrue(result is TransferResult.Retryable)
                if (failure in listOf("mismatch", "overflow")) assertTrue(result is TransferResult.Permanent)
                assertEquals("abc", file.readText())
                assertEquals(TransferCheckpoint() to "abc", snapshots.first())
                assertEquals(TransferCheckpoint("\"v1\"", 6) to "abc", snapshots.last())
                server.enqueue(segment(header, "def"))
                assertEquals(TransferResult.Verified(6), ResumableTransfer().attempt(file, checkpoint,
                    api::download, { checkpoint = it }, { _, _ -> }))
                assertEquals("abcdef", file.readText())
                assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
                assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
            }
        }
    }

    @Test fun crashSnapshotCannotResumeUnverifiedSegmentBytes() = runBlocking {
        for (header in listOf("Content-MD5", "Content-Digest")) {
            val file = temporary.newFile().apply { writeText("abc") }
            val snapshot = temporary.newFile()
            var checkpoint = TransferCheckpoint("\"v1\"", 6)
            var crashCheckpoint = checkpoint
            server.enqueue(segment(header, "X"))
            ResumableTransfer().attempt(file, checkpoint, api::download, { checkpoint = it }, { _, _ -> },
                afterWrite = {
                    snapshot.writeBytes(file.readBytes())
                    crashCheckpoint = checkpoint.copy()
                    throw IOException("process snapshot")
                })
            assertEquals("abcX", snapshot.readText())
            assertEquals(TransferCheckpoint(), crashCheckpoint)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("abcdef").setHeader("ETag", "\"v1\""))
            assertEquals(TransferResult.Verified(6), ResumableTransfer().attempt(snapshot, crashCheckpoint,
                api::download, { crashCheckpoint = it }, { _, _ -> }))
            val request = server.takeRequest()
            assertNull(request.getHeader("Range"))
            assertNull(request.getHeader("If-Range"))
            assertEquals("abcdef", snapshot.readText())
        }
    }

    @Test fun socketDisconnectRollsBackCorruptByteBeforeNextRange() = runBlocking {
        for (header in listOf("Content-MD5", "Content-Digest")) {
            val file = temporary.newFile().apply { writeText("abc") }
            var checkpoint = TransferCheckpoint("\"v1\"", 6)
            val value = if (header == "Content-MD5") digest("def", "MD5") else "sha-256=:${digest("def")}:"
            server.enqueue(MockResponse().setResponseCode(206).setBody("Xyz")
                .setHeader("ETag", "\"v1\"").setHeader("Content-Range", "bytes 3-5/6")
                .setHeader(header, value).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            var observed = ""
            assertTrue(ResumableTransfer().attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }, afterWrite = {
                    observed = file.readText()
                    assertEquals(TransferCheckpoint(), checkpoint)
                }) is TransferResult.Retryable)
            assertEquals("abcX", observed)
            assertEquals("abc", file.readText())
            server.takeRequest()
            server.enqueue(segment(header, "def"))
            assertEquals(TransferResult.Verified(6), ResumableTransfer().attempt(file, checkpoint,
                api::download, { checkpoint = it }, { _, _ -> }))
            assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
            assertEquals("abcdef", file.readText())
        }
    }

    @Test fun cancelledJobFinishesRollbackAndSuspendingCheckpointRestore() = runBlocking {
        for (header in listOf("Content-MD5", "Content-Digest")) {
            val file = temporary.newFile().apply { writeText("abc") }
            var checkpoint = TransferCheckpoint("\"v1\"", 6)
            server.enqueue(segment(header, "X"))
            val job = launch {
                ResumableTransfer().attempt(file, checkpoint, api::download, {
                    yield()
                    checkpoint = it
                    if (it.validator != null) assertEquals("abc", file.readText())
                }, { _, _ -> }, afterWrite = {
                    assertEquals(TransferCheckpoint(), checkpoint)
                    currentCoroutineContext().cancel()
                    currentCoroutineContext().ensureActive()
                })
            }
            job.join()
            assertTrue(job.isCancelled)
            assertEquals("abc", file.readText())
            assertEquals(TransferCheckpoint("\"v1\"", 6), checkpoint)
        }
    }

    @Test fun retainedRepresentationChecksumPreservesInterruptedIntermediateBytesAndDetectsCorruption() = runBlocking {
        for (header in listOf("Content-MD5", "Content-Digest")) {
            val file = temporary.newFile().apply { writeText("abc") }
            var checkpoint = TransferCheckpoint("\"v1\"", 6, representationChecksum = "sha-256=${digest("abcdef")}")
            server.enqueue(segment(header, "X", "de", "3-4/6"))
            assertTrue(ResumableTransfer().attempt(file, checkpoint, api::download, { checkpoint = it },
                { _, _ -> }, afterWrite = { throw IOException("synthetic disconnect") }) is TransferResult.Retryable)
            assertEquals("abcX", file.readText())
            assertEquals("\"v1\"", checkpoint.validator)
            server.enqueue(segment(header, "ef", "ef", "4-5/6"))
            assertTrue(ResumableTransfer().attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }) is TransferResult.Permanent)
            assertNull(checkpoint.validatedBytes)
            assertEquals("abcX", file.readText())
        }
    }
}
