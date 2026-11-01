package com.nendo.argosy.data.download.transfer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Streaming

class ResumableTransferTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var api: TestApi
    private val transfer = ResumableTransfer()

    interface TestApi {
        @Streaming @GET("fixture")
        @Headers("Accept-Encoding: identity")
        fun download(@Header("Range") range: String?, @Header("If-Range") ifRange: String?): Call<ResponseBody>
    }

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build())
            .build().create(TestApi::class.java)
    }

    @After fun tearDown() { server.shutdown() }

    private suspend fun attempt(file: File, checkpoint: TransferCheckpoint = TransferCheckpoint()): TransferResult =
        transfer.attempt(file, checkpoint, api::download, {}, { _, _ -> })

    private fun response(body: String = "abcdef") = MockResponse().setBody(body).setHeader("ETag", "\"v1\"")

    @Test fun initialRangeReplacesUnvalidatedPrefixAndMetadata() = runBlocking {
        val file = temporary.newFile().apply { writeText("stale-prefix") }
        val snapshots = mutableListOf<Pair<TransferCheckpoint, String>>()
        server.enqueue(response().setResponseCode(206).setHeader("Content-Range", "bytes 0-5/6"))
        val result = transfer.attempt(file, TransferCheckpoint(totalBytes = 99, representationChecksum = "invalid"),
            api::download, { snapshots += it to file.readText() }, { _, _ -> }, initialRange = true)
        assertEquals(TransferResult.Verified(6), result)
        assertEquals("abcdef", file.readText())
        assertEquals(TransferCheckpoint() to "stale-prefix", snapshots.first())
        assertEquals("", snapshots[1].second)
        assertNull(snapshots[1].first.representationChecksum)
        val request = server.takeRequest()
        assertEquals("bytes=0-", request.getHeader("Range"))
        assertNull(request.getHeader("If-Range"))
    }

    @Test fun initialChecksumSurvivesInterruptedTransferAndRejectsCorruptPrefix() = runBlocking {
        val bytes = ByteArray(256 * 1024) { (it % 127).toByte() }
        val digest = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
        val file = temporary.newFile()
        var checkpoint = TransferCheckpoint()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setResponseCode(206)
            .setHeader("Content-Range", "bytes 0-${bytes.lastIndex}/${bytes.size}")
            .setHeader("ETag", "\"v1\"").setHeader("Content-Digest", "sha-256=:$digest:")
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(transfer.attempt(file, checkpoint, api::download, { checkpoint = it }, { _, _ -> },
            initialRange = true) is TransferResult.Retryable)
        assertEquals("sha-256=$digest", checkpoint.representationChecksum)
        val offset = file.length().toInt()
        assertTrue(offset in 1 until bytes.size)
        RandomAccessFile(file, "rw").use { it.write(255) }
        val restored = TransferCheckpoint(checkpoint.validator, checkpoint.totalBytes,
            representationChecksum = checkpoint.representationChecksum)
        server.enqueue(response().setBody(Buffer().write(bytes, offset, bytes.size - offset)).setResponseCode(206)
            .setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}"))
        assertTrue(ResumableTransfer().attempt(file, restored, api::download, {}, { _, _ -> },
            initialRange = true) is TransferResult.Permanent)
    }

    @Test fun interruptedInitialResponseResumesOnlyWithQualifiedValidator() = runBlocking {
        for (validator in listOf("etag", "modified", "none", "fallback")) {
            val bytes = ByteArray(128 * 1024) { (it % 127).toByte() }
            val file = temporary.newFile()
            var checkpoint = TransferCheckpoint()
            val response = MockResponse().setBody(Buffer().write(bytes))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
            if (validator != "fallback") response.setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-${bytes.lastIndex}/${bytes.size}")
            val identity = when (validator) {
                "etag" -> "\"v1\""
                "modified" -> "Wed, 21 Oct 2015 07:28:00 GMT"
                else -> null
            }
            if (validator == "etag") response.setHeader("ETag", identity!!)
            if (validator == "modified") response.setHeader("Last-Modified", identity!!)
                .setHeader("Date", "Wed, 21 Oct 2015 07:30:00 GMT")
            server.enqueue(response)
            assertTrue(transfer.attempt(file, checkpoint, api::download, { checkpoint = it }, { _, _ -> },
                initialRange = true) is TransferResult.Retryable)
            val offset = if (identity == null) 0 else file.length().toInt()
            assertTrue(file.length() in 1 until bytes.size.toLong())
            val suffix = MockResponse().setBody(Buffer().write(bytes, offset, bytes.size - offset))
                .setResponseCode(206).setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
            if (validator == "etag") suffix.setHeader("ETag", identity!!)
            if (validator == "modified") suffix.setHeader("Last-Modified", identity!!)
                .setHeader("Date", "Wed, 21 Oct 2015 07:30:00 GMT")
            server.enqueue(suffix)
            assertEquals(TransferResult.Verified(bytes.size.toLong()), transfer.attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }, initialRange = true))
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("bytes=$offset-", request.getHeader("Range"))
            assertEquals(identity, request.getHeader("If-Range"))
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun initialShortRangeNeedsValidatorBeforeAnyMutation() = runBlocking {
        val file = temporary.newFile().apply { writeText("old") }
        server.enqueue(MockResponse().setBody("abc").setResponseCode(206).setHeader("Content-Range", "bytes 0-2/6"))
        assertTrue(transfer.attempt(file, TransferCheckpoint(), api::download,
            { error("Must not mutate checkpoint") }, { _, _ -> }, initialRange = true) is TransferResult.Permanent)
        assertEquals("old", file.readText())
        server.enqueue(response("abc").setResponseCode(206).setHeader("Content-Range", "bytes 0-2/6"))
        assertEquals(TransferResult.Continue, transfer.attempt(file, TransferCheckpoint(), api::download,
            {}, { _, _ -> }, initialRange = true))
        assertEquals("abc", file.readText())
    }

    @Test fun initialFullRangeAndFallbackAllowNoValidator() = runBlocking {
        for (partial in listOf(true, false)) {
            val file = temporary.newFile()
            val response = MockResponse().setBody("abcdef")
            if (partial) response.setResponseCode(206).setHeader("Content-Range", "bytes 0-5/6")
            server.enqueue(response)
            assertEquals(TransferResult.Verified(6), transfer.attempt(file, TransferCheckpoint(), api::download,
                {}, { _, _ -> }, initialRange = true))
            assertEquals("abcdef", file.readText())
        }
    }

    @Test fun malformedInitialRangesPreserveUnvalidatedData() = runBlocking {
        for (response in listOf(
            response().setHeader("Content-Range", "bytes 1-6/7"),
            response().setHeader("Content-Range", "bytes 0-4/6"),
            response().setHeader("Content-Range", "bytes 0-5/6").setHeader("Content-Encoding", "gzip")
        )) {
            val file = temporary.newFile().apply { writeText("old") }
            server.enqueue(response.setResponseCode(206))
            assertTrue(transfer.attempt(file, TransferCheckpoint(totalBytes = 99), api::download,
                { error("Must not mutate checkpoint") }, { _, _ -> }, initialRange = true) is TransferResult.Permanent)
            assertEquals("old", file.readText())
        }
    }

    @Test fun validPartialAndShortSegment() = runBlocking {
        val file = temporary.newFile().apply { writeText("abc") }
        var checkpoint = TransferCheckpoint("\"v1\"", 6)
        val checkpoints = mutableListOf<TransferCheckpoint>()
        server.enqueue(response("de").setResponseCode(206).setHeader("Content-Range", "bytes 3-4/6"))
        assertEquals(TransferResult.Continue, transfer.attempt(file, checkpoint, api::download,
            { checkpoint = it; checkpoints += it }, { _, _ -> }))
        assertTrue(checkpoints.isNotEmpty())
        assertTrue(checkpoints.all { it.validatedBytes == null })
        assertEquals("abcde", file.readText())
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        server.enqueue(response("f").setResponseCode(206).setHeader("Content-Range", "bytes 5-5/6"))
        assertEquals(TransferResult.Verified(6), ResumableTransfer().attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }))
        assertEquals(6L, checkpoint.validatedBytes)
    }

    @Test fun malformedRangesAndChangedIdentityPreservePrefix() = runBlocking {
        for (range in listOf("bytes 2-5/6", "bytes 3-6/6", "bytes 3-2/6", "bytes 3-5/*", "bytes 3-5/99999999999999999999")) {
            val file = temporary.newFile().apply { writeText("abc") }
            server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", range))
            assertTrue(attempt(file, TransferCheckpoint("\"v1\"", 6)) is TransferResult.Permanent)
            assertEquals("abc", file.readText())
        }
        val file = temporary.newFile().apply { writeText("abc") }
        server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "\"v2\""))
        assertTrue(attempt(file, TransferCheckpoint("\"v1\"", 6)) is TransferResult.Permanent)
        assertEquals("abc", file.readText())
    }

    @Test fun ignoredRangeInvalidatesBeforeResetAndAnnotatesAfterReset() = runBlocking {
        val file = temporary.newFile().apply { writeText("old") }
        val snapshots = mutableListOf<Pair<TransferCheckpoint, String>>()
        server.enqueue(response("new"))
        val result = transfer.attempt(file, TransferCheckpoint("\"old\"", 3), api::download,
            { snapshots += it to file.readText() }, { _, _ -> })
        assertEquals(TransferResult.Verified(3), result)
        assertEquals(TransferCheckpoint() to "old", snapshots[0])
        assertEquals(TransferCheckpoint("\"v1\"", 3) to "", snapshots[1])
        assertEquals(3L, snapshots.last().first.validatedBytes)
    }

    @Test fun rangeNotSatisfiableNeverPromotesEvenEqualSize() = runBlocking {
        for (range in listOf("bytes */3", "invalid")) {
            val file = temporary.newFile().apply { writeText("abc") }
            server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", range))
            assertEquals(TransferResult.RestartRequired, attempt(file, TransferCheckpoint("\"v1\"", 3)))
        }
    }

    @Test fun retryableAndPermanentHttpStatuses() = runBlocking {
        for (code in listOf(408, 429, 500, 502, 503, 504, 524)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "120"))
            assertEquals(TransferResult.Retryable("HTTP $code", "120"), attempt(temporary.newFile()))
        }
        for (code in listOf(204, 400, 401, 403, 404, 501)) {
            server.enqueue(MockResponse().setResponseCode(code))
            assertTrue(attempt(temporary.newFile()) is TransferResult.Permanent)
        }
    }

    @Test fun proxyTimeoutKeepsPrefixAndCheckpointForRetry() = runBlocking {
        val file = temporary.newFile().apply { writeText("abc") }
        val checkpoint = TransferCheckpoint("\"v1\"", 6)
        server.enqueue(MockResponse().setResponseCode(524).setHeader("Retry-After", "1"))
        assertEquals(TransferResult.Retryable("HTTP 524", "1"), transfer.attempt(file, checkpoint,
            api::download, { error("Proxy timeout must not change checkpoint") }, { _, _ -> }))
        assertEquals("abc", file.readText())
        server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6"))
        assertEquals(TransferResult.Verified(6), attempt(file, checkpoint))
        assertEquals("abcdef", file.readText())
        repeat(2) { assertEquals("bytes=3-", server.takeRequest().getHeader("Range")) }
    }

    @Test fun resetMidstreamResumesFromActualLength() = runBlocking {
        val bytes = ByteArray(128 * 1024) { (it % 251).toByte() }
        val file = temporary.newFile()
        var checkpoint = TransferCheckpoint()
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("ETag", "\"v1\"")
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(transfer.attempt(file, checkpoint, api::download, { checkpoint = it }, { _, _ -> }) is TransferResult.Retryable)
        val offset = file.length()
        assertTrue(offset in 1 until bytes.size.toLong())
        server.enqueue(MockResponse().setResponseCode(206).setBody(Buffer().write(bytes, offset.toInt(), bytes.size - offset.toInt()))
            .setHeader("ETag", "\"v1\"").setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}"))
        assertEquals(TransferResult.Verified(bytes.size.toLong()), attempt(file, checkpoint))
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun timeoutRetainsPartialAndRepeatedFailuresStayRetryable() = runBlocking {
        val file = temporary.newFile()
        var checkpoint = TransferCheckpoint()
        server.enqueue(response("a".repeat(100)).throttleBody(10, 1, TimeUnit.SECONDS))
        assertTrue(transfer.attempt(file, checkpoint, api::download, { checkpoint = it }, { _, _ -> }) is TransferResult.Retryable)
        assertTrue(file.length() in 1..99)
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(attempt(file) is TransferResult.Retryable)
        }
        val offset = file.length()
        server.enqueue(response("a".repeat((100 - offset).toInt())).setResponseCode(206)
            .setHeader("Content-Range", "bytes $offset-99/100"))
        assertEquals(TransferResult.Verified(100), attempt(file, checkpoint))
        assertEquals("a".repeat(100), file.readText())
    }

    @Test fun prematureEofAndInvalidEncoding() = runBlocking {
        server.enqueue(response("abc").setHeader("Content-Length", "6").setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        assertTrue(attempt(temporary.newFile()) is TransferResult.Retryable)
        server.enqueue(response().setHeader("Content-Encoding", "br"))
        assertTrue(attempt(temporary.newFile()) is TransferResult.Permanent)
        server.enqueue(MockResponse().setBody(""))
        assertTrue(attempt(temporary.newFile()) is TransferResult.Permanent)
    }

    @Test fun cancellationDuringHeadersAndBodyCancelsCall() = runBlocking {
        for (bodyPhase in listOf(false, true)) {
            val started = CompletableDeferred<Call<ResponseBody>>()
            val wrote = CompletableDeferred<Unit>()
            server.enqueue(if (bodyPhase) response("a".repeat(100000)).throttleBody(100, 1, TimeUnit.SECONDS)
                else MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = launch {
                transfer.attempt(temporary.newFile(), TransferCheckpoint(), { range, validator ->
                    api.download(range, validator).also { started.complete(it) }
                }, {}, { _, _ -> wrote.complete(Unit) })
            }
            val call = started.await()
            assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
            if (bodyPhase) withTimeout(2000) { wrote.await() }
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled)
        }
    }

    @Test fun independentConcurrentTransfers() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        val files = listOf(temporary.newFile(), temporary.newFile())
        val failures = files.map { async { attempt(it) } }.awaitAll()
        assertTrue(failures.all { it is TransferResult.Retryable })
        assertTrue(files.all { it.length() == 0L })
        server.enqueue(response("first"))
        server.enqueue(response("other"))
        val results = files.map { async { attempt(it) } }.awaitAll()
        assertTrue(results.all { it == TransferResult.Verified(5) })
        assertEquals(setOf("first", "other"), files.map { it.readText() }.toSet())
    }

    @Test fun syntheticZipWithAndWithoutStableValidator() = runBlocking {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("fixture.txt"))
                zip.write("generated fixture".toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
        for (validator in listOf("\"v1\"", "W/\"weak\"", null)) {
            val file = temporary.newFile().apply { writeBytes(bytes.copyOf(10)) }
            val resumable = validator == "\"v1\""
            val response = MockResponse().setBody(Buffer().write(if (resumable) bytes.copyOfRange(10, bytes.size) else bytes))
            if (resumable) response.setResponseCode(206).setHeader("ETag", validator)
                .setHeader("Content-Range", "bytes 10-${bytes.lastIndex}/${bytes.size}")
            server.enqueue(response)
            assertEquals(TransferResult.Verified(bytes.size.toLong()), attempt(file, TransferCheckpoint(validator, bytes.size.toLong())))
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(if (resumable) "bytes=10-" else null, server.takeRequest().getHeader("Range"))
        }
    }

    @Test fun http2UnknownLengthUsesStreamFraming() = runBlocking {
        val protocols = listOf(okhttp3.Protocol.H2_PRIOR_KNOWLEDGE)
        server.protocols = protocols
        val http2 = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().protocols(protocols).readTimeout(2, TimeUnit.SECONDS).build())
            .build().create(TestApi::class.java)
        server.enqueue(response().removeHeader("Content-Length"))
        val file = temporary.newFile()
        assertEquals(TransferResult.Verified(6), transfer.attempt(
            file, TransferCheckpoint(), http2::download, {}, { _, _ -> }
        ))
        assertEquals("abcdef", file.readText())
    }

    @Test fun sparseFileOffsetsExceedTwoGigabytes() = runBlocking {
        val file = temporary.newFile()
        val offset = Int.MAX_VALUE.toLong() + 123
        RandomAccessFile(file, "rw").use { it.setLength(offset) }
        server.enqueue(response("z").setResponseCode(206).setHeader("Content-Range", "bytes $offset-$offset/${offset + 1}"))
        assertEquals(TransferResult.Verified(offset + 1), attempt(file, TransferCheckpoint("\"v1\"", offset + 1)))
        assertEquals("bytes=$offset-", server.takeRequest().getHeader("Range"))
        assertEquals(offset + 1, file.length())
    }

    @Test fun diskFailuresArePermanent() = runBlocking {
        server.enqueue(response())
        assertTrue(attempt(temporary.newFolder()) is TransferResult.Permanent)
    }

    @Test fun callCreationConnectionFailureIsRetryable() = runBlocking {
        val result = transfer.attempt(temporary.newFile(), TransferCheckpoint(), { _, _ ->
            throw java.io.IOException("API unavailable")
        }, {}, { _, _ -> })
        assertEquals(TransferResult.Retryable("API unavailable"), result)
    }

    @Test fun checkpointStorageFailureIsPermanent() = runBlocking {
        server.enqueue(response())
        val result = transfer.attempt(temporary.newFile(), TransferCheckpoint(), api::download,
            { throw java.io.IOException("checkpoint storage failure") }, { _, _ -> })
        assertTrue(result is TransferResult.Permanent)
    }

    @Test fun tlsIdentityFailuresArePermanentButOtherHandshakeFailuresRetry() = runBlocking {
        val certificate = javax.net.ssl.SSLHandshakeException("certificate").apply {
            initCause(java.security.cert.CertificateException("untrusted"))
        }
        for (error in listOf(javax.net.ssl.SSLPeerUnverifiedException("hostname"), certificate)) {
            val result = transfer.attempt(temporary.newFile(), TransferCheckpoint(), { _, _ -> throw error }, {}, { _, _ -> })
            assertTrue(result is TransferResult.Permanent)
        }
        val result = transfer.attempt(temporary.newFile(), TransferCheckpoint(), { _, _ ->
            throw javax.net.ssl.SSLHandshakeException("connection closed")
        }, {}, { _, _ -> })
        assertTrue(result is TransferResult.Retryable)
    }

    @Test fun invalidMimePreservesOldPrefixAndNeverMarksValidated() = runBlocking {
        for (mime in listOf("image/jpeg", "text/html; charset=utf-8")) {
            val file = temporary.newFile().apply { writeText("old") }
            val checkpoints = mutableListOf<TransferCheckpoint>()
            server.enqueue(response("invalid").setHeader("Content-Type", mime))
            assertTrue(transfer.attempt(file, TransferCheckpoint("\"old\"", 3), api::download,
                { checkpoints += it }, { _, _ -> }) is TransferResult.Permanent)
            assertEquals("old", file.readText())
            assertTrue(checkpoints.isEmpty())
        }
    }

    private fun checksum(algorithm: String, text: String): String = java.util.Base64.getEncoder()
        .encodeToString(java.security.MessageDigest.getInstance(algorithm).digest(text.toByteArray()))

    @Test fun responseChecksumsHaveSegmentOrRepresentationScope() = runBlocking {
        for ((header, value) in listOf(
            "Content-MD5" to checksum("MD5", "def"),
            "Content-Digest" to "sha-256=:${checksum("SHA-256", "def")}:",
            "Digest" to "sha-256=${checksum("SHA-256", "abcdef")}",
            "Repr-Digest" to "sha-256=:${checksum("SHA-256", "abcdef")}:"
        )) {
            val file = temporary.newFile().apply { writeText("abc") }
            var checkpoint = TransferCheckpoint("\"v1\"", 6)
            server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6")
                .setHeader(header, value))
            assertEquals(TransferResult.Verified(6), transfer.attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }))
            assertEquals("abcdef", file.readText())
            assertEquals(6L, checkpoint.validatedBytes)
        }
    }

    @Test fun checksumMismatchRollsBackAndMalformedChecksumNeverWrites() = runBlocking {
        for ((header, value) in listOf(
            "Content-MD5" to checksum("MD5", "bad"),
            "Content-Digest" to "sha-256=:${checksum("SHA-256", "bad")}:",
            "Digest" to "sha-256=${checksum("SHA-256", "abcdefg")}",
            "Repr-Digest" to "sha-256=:${checksum("SHA-256", "abcdefg")}:",
            "Content-MD5" to "not-base64",
            "Content-Digest" to "sha-256=malformed"
        )) {
            val file = temporary.newFile().apply { writeText("abc") }
            var checkpoint = TransferCheckpoint("\"v1\"", 6)
            server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6")
                .setHeader(header, value))
            assertTrue(transfer.attempt(file, checkpoint, api::download,
                { checkpoint = it }, { _, _ -> }) is TransferResult.Permanent)
            assertEquals("abc", file.readText())
            assertNull(checkpoint.validatedBytes)
        }
    }

    @Test fun freshChecksumAndIntermediateRepresentationDigest() = runBlocking {
        val file = temporary.newFile()
        server.enqueue(response().setHeader("Content-Digest", "sha-256=:${checksum("SHA-256", "abcdef")}:"))
        assertEquals(TransferResult.Verified(6), attempt(file))
        file.writeText("abc")
        server.enqueue(response("de").setResponseCode(206).setHeader("Content-Range", "bytes 3-4/6")
            .setHeader("Repr-Digest", "sha-256=:${checksum("SHA-256", "abcdef")}:"))
        var checkpoint = TransferCheckpoint("\"v1\"", 6)
        assertEquals(TransferResult.Continue, transfer.attempt(file, checkpoint, api::download,
            { checkpoint = it }, { _, _ -> }))
        assertNull(checkpoint.validatedBytes)
        assertEquals("abcde", file.readText())
    }

    @Test fun chunkedFramingIsAcceptedButCloseDelimitedIsRejected() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("abcdef", 2))
        assertEquals(TransferResult.Verified(6), attempt(temporary.newFile()))
        server.enqueue(response().removeHeader("Content-Length").setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
        assertTrue(attempt(temporary.newFile()) is TransferResult.Permanent)
    }

    @Test fun lastModifiedRequiresQualificationAndNoEtag() = runBlocking {
        val modified = "Thu, 01 Jan 1970 00:00:00 GMT"
        for ((date, etag, expected) in listOf(
            Triple("Thu, 01 Jan 1970 00:01:00 GMT", null, modified),
            Triple("Thu, 01 Jan 1970 00:00:59 GMT", null, null),
            Triple("Thu, 01 Jan 1970 00:02:00 GMT", "W/\"weak\"", null)
        )) {
            var checkpoint = TransferCheckpoint()
            val response = MockResponse().setBody("abcdef").setHeader("Last-Modified", modified).setHeader("Date", date)
            if (etag != null) response.setHeader("ETag", etag)
            server.enqueue(response)
            transfer.attempt(temporary.newFile(), checkpoint, api::download, { checkpoint = it }, { _, _ -> })
            assertEquals(expected, checkpoint.validator)
        }
        val file = temporary.newFile().apply { writeText("abc") }
        server.enqueue(MockResponse().setResponseCode(206).setBody("def")
            .setHeader("Last-Modified", modified).setHeader("Date", "Thu, 01 Jan 1970 00:02:00 GMT")
            .setHeader("Content-Range", "bytes 3-5/6"))
        assertEquals(TransferResult.Verified(6), attempt(file, TransferCheckpoint(modified, 6)))
    }

    @Test fun partialLengthAndTotalMustAgree() = runBlocking {
        for ((length, total) in listOf(2 to 6L, 3 to 7L)) {
            val file = temporary.newFile().apply { writeText("abc") }
            server.enqueue(response("def").setResponseCode(206).setHeader("Content-Range", "bytes 3-5/6")
                .setHeader("Content-Length", length))
            assertTrue(attempt(file, TransferCheckpoint("\"v1\"", total)) is TransferResult.Permanent)
            assertEquals("abc", file.readText())
        }
    }
}
