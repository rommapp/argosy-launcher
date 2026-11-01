package com.nendo.argosy.data.download

import com.nendo.argosy.data.download.transfer.DownloadRetryPolicy
import com.nendo.argosy.data.download.transfer.TransferCheckpoint
import com.nendo.argosy.data.download.transfer.TransferResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadTransferRunnerTest {
    @get:Rule val storage = TemporaryFolder()

    @Test fun `open stalled socket is cancelled offline and resumes disk prefix`() = stalledSocket(false)

    @Test fun `user cancellation during socket stall prevents reconnect request`() = stalledSocket(true)

    private fun stalledSocket(userCancel: Boolean) = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            val api = retrofit2.Retrofit.Builder().baseUrl(server.url("/"))
                .client(okhttp3.OkHttpClient.Builder().readTimeout(900, java.util.concurrent.TimeUnit.SECONDS).build()).build()
                .create(com.nendo.argosy.data.download.transfer.ResumableTransferTest.TestApi::class.java)
            val bytes = ByteArray(256 * 1024) { (it % 127).toByte() }
            val file = storage.newFile()
            val online = MutableStateFlow(true)
            val waiting = kotlinx.coroutines.CompletableDeferred<Unit>()
            val progress = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            var checkpoint = TransferCheckpoint()
            var captured: retrofit2.Call<okhttp3.ResponseBody>? = null
            val runner = DownloadTransferRunner(DownloadConnectivity { online }, DownloadRetryPolicy { 0.0 })
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(okio.Buffer().write(bytes, 0, 65536))
                .setResponseCode(206).setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 0-${bytes.lastIndex}/${bytes.size}")
                .setHeader("Content-Length", bytes.size))
            val result = async {
                runner.run(file, TransferCheckpoint(), 0, null, { range, validator ->
                    val call = api.download(range, validator)
                    captured = call
                    object : retrofit2.Call<okhttp3.ResponseBody> by call {
                        override fun cancel() {
                            call.cancel()
                            cancelled.complete(Unit)
                        }
                    }
                }, { checkpoint = it },
                    persistRetry = { _, _ -> },
                    onState = { if (it == DownloadState.WAITING_FOR_NETWORK) waiting.complete(Unit) },
                    onProgress = { count, _ -> if (count > 0) progress.complete(Unit) },
                    beforeRead = {}, afterWrite = {}, initialRange = true)
            }
            kotlinx.coroutines.withTimeout(10000) { progress.await() }
            if (userCancel) {
                kotlinx.coroutines.withTimeout(3000) { result.cancelAndJoin() }
                assertTrue(result.isCancelled)
                assertTrue(captured!!.isCanceled)
                assertEquals(0, online.subscriptionCount.value)
                online.value = false
                online.value = true
                assertEquals(1, server.requestCount)
                return@runBlocking
            }
            online.value = false
            kotlinx.coroutines.withTimeout(3000) { cancelled.await() }
            assertTrue(captured!!.isCanceled)
            kotlinx.coroutines.withTimeout(10000) { waiting.await() }
            val offset = file.length().toInt()
            assertTrue(offset in 1 until bytes.size)
            assertEquals(1, server.requestCount)
            assertEquals("\"v1\"", checkpoint.validator)
            server.enqueue(okhttp3.mockwebserver.MockResponse()
                .setBody(okio.Buffer().write(bytes, offset, bytes.size - offset)).setResponseCode(206)
                .setHeader("ETag", "\"v1\"").setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}"))
            online.value = true
            assertEquals(TransferResult.Verified(bytes.size.toLong()), kotlinx.coroutines.withTimeout(10000) { result.await() })
            val initial = server.takeRequest()
            assertEquals("bytes=0-", initial.getHeader("Range"))
            org.junit.Assert.assertNull(initial.getHeader("If-Range"))
            val resumed = server.takeRequest()
            assertEquals("bytes=$offset-", resumed.getHeader("Range"))
            assertEquals("\"v1\"", resumed.getHeader("If-Range"))
            org.junit.Assert.assertArrayEquals(bytes, file.readBytes())
            assertEquals(0, online.subscriptionCount.value)
        } finally {
            server.shutdown()
        }
    }

    private suspend fun DownloadTransferRunner.runFixture(
        attempt: Int = 0,
        retryAt: Long? = null,
        retry: suspend (Int, Long?) -> Unit = { _, _ -> },
        state: suspend (DownloadState) -> Unit = {},
        newCall: (String?, String?) -> retrofit2.Call<okhttp3.ResponseBody> = { _, _ ->
            error("Synthetic transfer must not create a network call")
        }
    ) = run(
        storage.newFile(), TransferCheckpoint(), attempt, retryAt,
        newCall = newCall,
        persist = {}, persistRetry = retry, onState = state,
        onProgress = { _, _ -> }, beforeRead = {}, afterWrite = {}
    )

    private fun unexecutedCall(): retrofit2.Call<okhttp3.ResponseBody> = retrofit2.Retrofit.Builder()
        .baseUrl("http://127.0.0.1/").build()
        .create(com.nendo.argosy.data.download.transfer.ResumableTransferTest.TestApi::class.java)
        .download(null, null)

    @Test fun `loss before publication cancels the original newly created call`() = runTest {
        var subscriptions = 0
        val original = unexecutedCall()
        val permanent = TransferResult.Permanent("unchanged")
        val runner = DownloadTransferRunner(
            DownloadConnectivity { flowOf(++subscriptions == 1) },
            transfer = { _, _, call, _, _, _, _, _, _ ->
                org.junit.Assert.assertSame(original, call(null, null))
                assertTrue(original.isCanceled)
                permanent
            }
        )
        org.junit.Assert.assertSame(permanent, runner.runFixture(newCall = { _, _ -> original }))
    }

    @Test fun `finite online flow does not cancel a delayed attempt or alter permanent result`() = runTest {
        val original = unexecutedCall()
        val permanent = TransferResult.Permanent("unchanged")
        val runner = DownloadTransferRunner(
            DownloadConnectivity { flowOf(true) },
            transfer = { _, _, call, _, _, _, _, _, _ ->
                call(null, null)
                delay(10000)
                assertFalse(original.isCanceled)
                permanent
            }
        )
        org.junit.Assert.assertSame(permanent, runner.runFixture(newCall = { _, _ -> original }))
    }

    @Test fun `attempt monitors unsubscribe and never cancel an earlier call`() = runTest {
        val online = MutableStateFlow(true)
        val first = unexecutedCall()
        val second = unexecutedCall()
        var attempts = 0
        val runner = DownloadTransferRunner(
            DownloadConnectivity { online },
            transfer = { _, _, call, _, _, _, _, _, _ ->
                attempts++
                assertEquals(1, online.subscriptionCount.value)
                call(null, null)
                if (attempts == 1) TransferResult.Continue else {
                    awaitCancellation()
                }
            }
        )
        val job = launch { runner.runFixture(newCall = { _, _ -> if (attempts == 1) first else second }) }
        runCurrent()
        assertEquals(2, attempts)
        online.value = false
        runCurrent()
        assertFalse(first.isCanceled)
        assertTrue(second.isCanceled)
        job.cancelAndJoin()
        assertEquals(0, online.subscriptionCount.value)
    }

    @Test fun `online delayed headers complete normally`() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()
        try {
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("payload")
                .setHeadersDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS))
            val api = retrofit2.Retrofit.Builder().baseUrl(server.url("/")).build()
                .create(com.nendo.argosy.data.download.transfer.ResumableTransferTest.TestApi::class.java)
            val online = MutableStateFlow(true)
            val result = kotlinx.coroutines.withTimeout(10000) {
                DownloadTransferRunner(DownloadConnectivity { online }).runFixture(newCall = api::download)
            }
            assertEquals(TransferResult.Verified(7), result)
            assertEquals(0, online.subscriptionCount.value)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cancelling backoff prevents another attempt`() = runTest {
        var calls = 0
        val states = mutableListOf<DownloadState>()
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable, DownloadRetryPolicy { 0.5 },
            nowMillis = { testScheduler.currentTime },
            transfer = { _, _, _, _, _, _, _, _, _ -> calls++; TransferResult.Retryable("offline") }
        )
        val job = launch { runner.runFixture(state = { states += it }) }
        runCurrent()
        assertEquals(1, calls)
        assertEquals(DownloadState.RETRYING, states.last())
        job.cancelAndJoin()
        advanceTimeBy(60000)
        assertEquals(1, calls)
    }

    @Test
    fun `reconnection cannot bypass durable retry after deadline`() = runTest {
        val online = MutableStateFlow(false)
        var calls = 0
        val runner = DownloadTransferRunner(
            DownloadConnectivity { online }, nowMillis = { testScheduler.currentTime },
            transfer = { _, _, _, _, _, _, _, _, _ -> calls++; TransferResult.Verified(1024) }
        )
        val job = launch { runner.runFixture(retryAt = 10000) }
        runCurrent()
        online.value = true
        advanceTimeBy(9999)
        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, calls)
        job.join()
    }

    @Test
    fun `offline flow waits for callback and unsubscribes after cancellation`() = runTest {
        val online = MutableStateFlow(false)
        var calls = 0
        val states = mutableListOf<DownloadState>()
        val runner = DownloadTransferRunner(
            DownloadConnectivity { online },
            transfer = { _, _, _, _, _, _, _, _, _ -> calls++; TransferResult.Verified(1024) }
        )
        val job = launch { runner.runFixture(state = { states += it }) }
        runCurrent()
        assertEquals(DownloadState.WAITING_FOR_NETWORK, states.last())
        assertEquals(1, online.subscriptionCount.value)
        assertEquals(0, calls)
        job.cancelAndJoin()
        assertEquals(0, online.subscriptionCount.value)
    }

    @Test
    fun `meaningful progress across failed attempts resets backoff`() = runTest {
        var calls = 0
        val retries = mutableListOf<Int>()
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable, DownloadRetryPolicy { 0.5 },
            nowMillis = { testScheduler.currentTime },
            transfer = { _, _, _, _, progress, _, _, _, _ ->
                calls++
                progress(calls * 600000L, 3000000L)
                if (calls < 3) TransferResult.Retryable("disconnect") else TransferResult.Verified(3000000)
            }
        )
        runner.runFixture(attempt = 5, retry = { count, at -> if (at != null || count == 0) retries += count })
        assertEquals(listOf(6, 0, 1, 0), retries)
    }

    @Test
    fun `safe restart budget survives transient failures`() = runTest {
        var calls = 0
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable, DownloadRetryPolicy { 0.5 },
            nowMillis = { testScheduler.currentTime },
            transfer = { _, _, _, _, _, _, _, _, _ ->
                calls++
                if (calls == 2) TransferResult.Retryable("disconnect") else TransferResult.RestartRequired
            }
        )
        assertTrue(runner.runFixture() is TransferResult.Permanent)
        assertEquals(3, calls)
    }

    @Test
    fun `unavailable API during identity resolution retries durably`() = runTest {
        var calls = 0
        val attempts = mutableListOf<Int>()
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable, DownloadRetryPolicy { 0.5 },
            nowMillis = { testScheduler.currentTime }
        )
        val key = runner.resolveRequestKey(
            initialAttempt = 0, initialRetryAt = null,
            resolve = {
                calls++
                if (calls < 3) throw java.io.IOException("API unavailable")
                "synthetic-request"
            },
            persistRetry = { count, at -> if (at != null) attempts += count },
            onState = {}
        )
        assertEquals("synthetic-request", key)
        assertEquals(listOf(1, 2), attempts)
        assertEquals(3000L, testScheduler.currentTime)
    }

    @Test
    fun `identity resolution retry is cancellable`() = runTest {
        var calls = 0
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable, DownloadRetryPolicy { 0.5 },
            nowMillis = { testScheduler.currentTime }
        )
        val job = launch {
            runner.resolveRequestKey(0, null,
                resolve = { calls++; throw java.io.IOException("API unavailable") },
                persistRetry = { _, _ -> }, onState = {})
        }
        runCurrent()
        job.cancelAndJoin()
        advanceTimeBy(60000)
        assertEquals(1, calls)
    }

    @Test
    fun `healthy progress does not write retry state per mebibyte`() = runTest {
        var writes = 0
        val runner = DownloadTransferRunner(
            DownloadConnectivity.AlwaysAvailable,
            transfer = { _, _, _, _, progress, _, _, _, _ ->
                repeat(8) { progress((it + 1) * 1048576L, 8388608L) }
                TransferResult.Verified(8388608L)
            }
        )
        runner.runFixture(retry = { _, _ -> writes++ })
        assertEquals(1, writes)
    }
}
