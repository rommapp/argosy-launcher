package com.nendo.argosy.data.sync

import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformFailureReason
import com.nendo.argosy.domain.usecase.sync.SyncPlatformResult
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSyncQueueTest {

    private val libraryGate = CompletableDeferred<Unit>()
    private val platformRan = CompletableDeferred<Long>()

    private val syncLibrary = mockk<SyncLibraryUseCase> {
        coEvery { this@mockk.invoke(any(), any(), any()) } coAnswers {
            libraryGate.await()
            SyncLibraryResult.AlreadyInProgress
        }
    }
    private val syncPlatform = mockk<SyncPlatformUseCase> {
        coEvery { this@mockk.invoke(any(), any()) } coAnswers {
            platformRan.complete(firstArg())
            SyncPlatformResult.Error(SyncPlatformFailureReason.NotConnected)
        }
    }

    @Test
    fun `a platform queued before library busy is published runs once after the library ends`() = runTest {
        val queue = PlatformSyncQueue({ syncPlatform }, { syncLibrary }, backgroundScope)
        assertTrue(queue.enqueueLibrary())
        queue.enqueuePlatformAfterLibrary(7L, "Saturn")
        assertEquals(setOf(7L), queue.queuedPlatformIds.value)
        queue.enqueuePlatformAfterLibrary(7L, "Saturn")
        assertEquals(setOf(7L), queue.queuedPlatformIds.value)

        runCurrent()

        assertTrue(queue.activeJob.value is PlatformSyncQueue.Job.Library)
        assertFalse(queue.enqueuePlatform(8L, "Dreamcast"))
        assertFalse(platformRan.isCompleted)
        coVerify(exactly = 0) { syncPlatform.invoke(any(), any()) }

        libraryGate.complete(Unit)
        runCurrent()

        assertTrue(platformRan.isCompleted)
        assertEquals(7L, platformRan.getCompleted())
        coVerify(exactly = 1) { syncPlatform.invoke(7L, "Saturn") }
        assertNull(queue.activeJob.value)
        assertTrue(queue.queuedPlatformIds.value.isEmpty())
        assertFalse(queue.isLibraryBusyNow())
    }

    @Test
    fun `a platform queued while library busy is published runs after the library ends`() = runTest {
        val queue = PlatformSyncQueue({ syncPlatform }, { syncLibrary }, backgroundScope)
        assertTrue(queue.enqueueLibrary())
        runCurrent()
        assertTrue(queue.isLibraryBusy.value)

        queue.enqueuePlatformAfterLibrary(7L, "Saturn")
        assertEquals(setOf(7L), queue.queuedPlatformIds.value)
        assertFalse(queue.enqueuePlatform(8L, "Dreamcast"))
        runCurrent()
        assertFalse(platformRan.isCompleted)
        coVerify(exactly = 0) { syncPlatform.invoke(any(), any()) }

        libraryGate.complete(Unit)
        runCurrent()

        assertTrue(platformRan.isCompleted)
        assertEquals(7L, platformRan.getCompleted())
        coVerify(exactly = 1) { syncPlatform.invoke(7L, "Saturn") }
        assertNull(queue.activeJob.value)
        assertTrue(queue.queuedPlatformIds.value.isEmpty())
        assertFalse(queue.isLibraryBusy.value)
    }

    @Test
    fun `a platform queued after the library runs at once when no library sync is running`() = runTest {
        val queue = PlatformSyncQueue({ syncPlatform }, { syncLibrary }, backgroundScope)
        queue.enqueuePlatformAfterLibrary(7L, "Saturn")
        assertEquals(setOf(7L), queue.queuedPlatformIds.value)
        runCurrent()

        assertTrue(platformRan.isCompleted)
        assertEquals(7L, platformRan.getCompleted())
        coVerify(exactly = 1) { syncPlatform.invoke(7L, "Saturn") }
        assertNull(queue.activeJob.value)
        assertTrue(queue.queuedPlatformIds.value.isEmpty())
    }

    @Test
    fun `a queued platform runs when the active drain is cancelled`() = runTest {
        val platformGate = CompletableDeferred<Unit>()
        coEvery { syncPlatform.invoke(7L, "Saturn") } coAnswers {
            platformGate.await()
            throw CancellationException("Platform sync cancelled")
        }
        val queue = PlatformSyncQueue({ syncPlatform }, { syncLibrary }, backgroundScope)
        assertTrue(queue.enqueuePlatform(7L, "Saturn"))
        runCurrent()
        assertEquals(PlatformSyncQueue.Job.Platform(7L, "Saturn"), queue.activeJob.value)

        assertTrue(queue.enqueuePlatform(8L, "Dreamcast"))
        runCurrent()
        assertEquals(setOf(7L, 8L), queue.queuedPlatformIds.value)
        assertFalse(platformRan.isCompleted)
        coVerify(exactly = 0) { syncPlatform.invoke(8L, "Dreamcast") }

        platformGate.complete(Unit)
        runCurrent()

        assertTrue(platformRan.isCompleted)
        assertEquals(8L, platformRan.getCompleted())
        coVerify(exactly = 1) { syncPlatform.invoke(7L, "Saturn") }
        coVerify(exactly = 1) { syncPlatform.invoke(8L, "Dreamcast") }
        assertNull(queue.activeJob.value)
        assertTrue(queue.queuedPlatformIds.value.isEmpty())
    }
}
