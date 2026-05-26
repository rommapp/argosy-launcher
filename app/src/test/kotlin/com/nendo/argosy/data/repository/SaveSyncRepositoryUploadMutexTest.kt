package com.nendo.argosy.data.repository

import com.nendo.argosy.data.sync.SyncQueueManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SaveSyncRepositoryUploadMutexTest {

    private val apiClient = mockk<SaveSyncApiClient>(relaxed = true)
    private val conflictResolver = mockk<SaveSyncConflictResolver>(relaxed = true)
    private val orchestrator = mockk<SaveSyncOrchestrator>(relaxed = true)
    private val entityManager = mockk<SaveSyncEntityManager>(relaxed = true)
    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)

    private lateinit var repo: SaveSyncRepository

    @Before
    fun setUp() {
        repo = SaveSyncRepository(
            apiClient, conflictResolver, orchestrator, entityManager,
            stateCacheManager, syncQueueManager,
        )
    }

    @Test
    fun `same gameId and channel serializes uploads`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val inflight = AtomicInteger(0)
        val maxInflight = AtomicInteger(0)
        coEvery { apiClient.uploadSave(1L, any(), any(), any(), any(), any()) } coAnswers {
            val now = inflight.incrementAndGet()
            maxInflight.updateAndGet { if (now > it) now else it }
            gate.await()
            inflight.decrementAndGet()
            SaveSyncResult.Success()
        }

        coroutineScope {
            val a = async { repo.uploadSave(1L, "eden", channelName = null) }
            val b = async { repo.uploadSave(1L, "eden", channelName = null) }
            yieldUntil { inflight.get() >= 1 }
            gate.complete(Unit)
            a.await()
            b.await()
        }

        assertTrue("Max concurrent uploads for same key must be 1, was ${maxInflight.get()}", maxInflight.get() == 1)
    }

    @Test
    fun `different gameId runs in parallel`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val inflight = AtomicInteger(0)
        val maxInflight = AtomicInteger(0)
        coEvery { apiClient.uploadSave(any(), any(), any(), any(), any(), any()) } coAnswers {
            val now = inflight.incrementAndGet()
            maxInflight.updateAndGet { if (now > it) now else it }
            gate.await()
            inflight.decrementAndGet()
            SaveSyncResult.Success()
        }

        coroutineScope {
            val a = async { repo.uploadSave(1L, "eden", channelName = null) }
            val b = async { repo.uploadSave(2L, "eden", channelName = null) }
            yieldUntil { inflight.get() >= 2 }
            gate.complete(Unit)
            a.await()
            b.await()
        }

        assertTrue("Different gameIds must run in parallel, max inflight was ${maxInflight.get()}", maxInflight.get() == 2)
    }

    @Test
    fun `different channel for same gameId runs in parallel`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val inflight = AtomicInteger(0)
        val maxInflight = AtomicInteger(0)
        coEvery { apiClient.uploadSave(any(), any(), any(), any(), any(), any()) } coAnswers {
            val now = inflight.incrementAndGet()
            maxInflight.updateAndGet { if (now > it) now else it }
            gate.await()
            inflight.decrementAndGet()
            SaveSyncResult.Success()
        }

        coroutineScope {
            val a = async { repo.uploadSave(1L, "eden", channelName = null) }
            val b = async { repo.uploadSave(1L, "eden", channelName = "manual") }
            yieldUntil { inflight.get() >= 2 }
            gate.complete(Unit)
            a.await()
            b.await()
        }

        assertTrue("Distinct channels for same gameId must run in parallel, max inflight was ${maxInflight.get()}", maxInflight.get() == 2)
    }

    @Test
    fun `null channel and named channel are distinct mutex keys`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var nullChannelCompleted = false
        coEvery { apiClient.uploadSave(1L, any(), null, any(), any(), any()) } coAnswers {
            gate.await()
            nullChannelCompleted = true
            SaveSyncResult.Success()
        }
        coEvery { apiClient.uploadSave(1L, any(), "named", any(), any(), any()) } coAnswers {
            SaveSyncResult.Success()
        }

        coroutineScope {
            val nullJob = async { repo.uploadSave(1L, "eden", channelName = null) }
            yield()
            val namedJob = async { repo.uploadSave(1L, "eden", channelName = "named") }
            withTimeout(1_000) { namedJob.await() }
            assertFalse("Named-channel upload completed before null-channel was unblocked", nullChannelCompleted)
            gate.complete(Unit)
            nullJob.await()
        }
    }

    private suspend fun yieldUntil(predicate: () -> Boolean) {
        var spins = 0
        while (!predicate()) {
            if (spins++ > 10_000) error("yieldUntil exceeded spin budget; condition never satisfied")
            yield()
            delay(1)
        }
    }
}
