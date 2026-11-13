package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.sync.SaveRecoveryGate
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.util.PermissionHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PlaySessionTrackerNoticesTest {
    private val gameId = 7L
    private val emulatorPackage = "test.emulator"

    private val gameDao: GameDao = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val permissionHelper: PermissionHelper = mockk(relaxed = true)
    private val sessionSaveFinalizer: SessionSaveFinalizer = mockk()
    private val notices: SessionEndNotices = mockk(relaxed = true)
    private val screenshotScanner: SessionScreenshotScanner = mockk(relaxed = true)

    private val persisted = PersistedSession(
        gameId = gameId,
        emulatorPackage = emulatorPackage,
        startTime = Instant.now(),
        coreName = null,
        isHardcore = false
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(any()) } returns null
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        coEvery { preferencesRepository.getPersistedSession() } returns null
    }

    private inline fun <reified T : Any> lazyMock(): dagger.Lazy<T> {
        val instance = mockk<T>(relaxed = true)
        return dagger.Lazy<T> { instance }
    }

    private fun tracker() = PlaySessionTracker(
        application = mockk(relaxed = true),
        gameDao = gameDao,
        overlayWriter = mockk(relaxed = true),
        activeSaveRepository = mockk(relaxed = true),
        playSessionDao = mockk(relaxed = true),
        syncStatesOnSessionEndUseCase = lazyMock(),
        saveCacheManager = lazyMock(),
        saveSyncRepository = lazyMock(),
        romMRepository = lazyMock(),
        preferencesRepository = preferencesRepository,
        permissionHelper = permissionHelper,
        gameUpdateBus = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        fileAccessLayer = mockk(relaxed = true),
        socialRepository = lazyMock(),
        saveRecoveryGate = SaveRecoveryGate(),
        reconcileAchievementsOnSessionEndUseCase = lazyMock(),
        savePathAuthority = mockk(relaxed = true),
        sessionSaveFinalizer = sessionSaveFinalizer,
        sessionEndNotices = notices,
        snapshotRouter = lazyMock(),
        sessionRecapFactsRecorder = mockk(relaxed = true),
        sessionScreenshotScanner = screenshotScanner,
        streamClaimReleaser = lazyMock()
    )

    private suspend fun trackerWithLiveSession(): PlaySessionTracker {
        coEvery { preferencesRepository.getPersistedSession() } returns persisted
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns true
        val tracker = tracker()
        tracker.checkOrphanedSession()
        coEvery { preferencesRepository.getPersistedSession() } returns null
        return tracker
    }

    @Test
    fun `a live session end whose save could not be cached posts the not-kept notice`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } returns SessionSaveOutcome.CacheFailed
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 1) { notices.sessionSaveNotKept(gameId) }
        coVerify(exactly = 0) { notices.sessionSaveSynced(any(), any()) }
    }

    @Test
    fun `a cancel while the not-kept notice posts counts the failed capture once`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } returns SessionSaveOutcome.CacheFailed
        coEvery { notices.sessionSaveNotKept(gameId) } throws CancellationException("left")
        val tracker = trackerWithLiveSession()

        runCatching { tracker.endSession(stopService = false) }

        coVerify(exactly = 1) { preferencesRepository.keepSessionForRetry(any(), any()) }
    }

    @Test
    fun `a live session end whose save work throws posts no notice`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } throws IOException("db")
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
        coVerify(exactly = 0) { notices.sessionSaveSynced(any(), any()) }
        coVerify(exactly = 0) { notices.orphanSaveRecovered(any(), any()) }
    }

    @Test
    fun `sessionEnding holds through the save work and its conflict is pending when it clears`() = runTest {
        val tracker = trackerWithLiveSession()
        val conflict = SyncSaveOnSessionEndUseCase.Result.Conflict(
            gameId = gameId,
            emulatorId = "test-emulator",
            channelName = null,
            upload = SaveSyncResult.Conflict(gameId, Instant.EPOCH, Instant.EPOCH)
        )
        var endingDuringSave = false
        coEvery { sessionSaveFinalizer.finalize(any()) } coAnswers {
            endingDuringSave = tracker.sessionEnding.value
            SessionSaveOutcome.Synced(SaveCacheManager.CacheResult.Created(timestamp = 1L), conflict, conflictId = 1L)
        }
        val pendingWhenCleared = mutableListOf<SaveConflictEvent?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            tracker.sessionEnding.drop(1).filter { !it }.collect {
                pendingWhenCleared += tracker.pendingSessionConflict.value
            }
        }

        tracker.endSession(stopService = false)

        assertTrue(endingDuringSave)
        assertEquals(gameId, pendingWhenCleared.single()?.gameId)
    }

    @Test
    fun `a live session end whose save synced posts the sync result`() = runTest {
        val uploaded = SyncSaveOnSessionEndUseCase.Result.Uploaded()
        coEvery { sessionSaveFinalizer.finalize(any()) } returns
            SessionSaveOutcome.Synced(SaveCacheManager.CacheResult.Created(timestamp = 1L), uploaded, conflictId = null)
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 1) { notices.sessionSaveSynced(gameId, uploaded) }
        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
    }

    @Test
    fun `a session end with only a persisted record reports through orphan recovery`() = runTest {
        val outcome = SessionSaveOutcome.Synced(
            SaveCacheManager.CacheResult.Created(timestamp = 1L),
            SyncSaveOnSessionEndUseCase.Result.Queued,
            conflictId = null
        )
        coEvery { sessionSaveFinalizer.finalize(any()) } returns outcome
        coEvery { preferencesRepository.getPersistedSession() } returns persisted

        tracker().endSession(stopService = false)

        coVerify(exactly = 1) { notices.orphanSaveRecovered(gameId, outcome) }
        coVerify(exactly = 0) { notices.sessionSaveSynced(any(), any()) }
        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
    }

    @Test
    fun `startup recovery of a killed session scans no screenshots, since its end is unknown`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } returns SessionSaveOutcome.NothingToSave
        coEvery { preferencesRepository.getPersistedSession() } returns persisted
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns false

        tracker().checkOrphanedSession()

        coVerify(exactly = 0) { screenshotScanner.queueSessionScreenshots(any(), any(), any(), any()) }
    }

    @Test
    fun `a live session end scans the session's window for screenshots`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } returns SessionSaveOutcome.NothingToSave
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 1) {
            screenshotScanner.queueSessionScreenshots(gameId, persisted.emulatorPackage, persisted.startTime, any())
        }
    }

    @Test
    fun `a failing screenshot scan does not change how the session end turns out`() = runTest {
        val uploaded = SyncSaveOnSessionEndUseCase.Result.Uploaded()
        coEvery { sessionSaveFinalizer.finalize(any()) } returns
            SessionSaveOutcome.Synced(SaveCacheManager.CacheResult.Created(timestamp = 1L), uploaded, conflictId = null)
        coEvery { screenshotScanner.queueSessionScreenshots(any(), any(), any(), any()) } throws IOException("disk")
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 1) { notices.sessionSaveSynced(gameId, uploaded) }
    }

    @Test
    fun `an orphan check with usage stats unavailable keeps the session open and archives nothing`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persisted
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns null
        val tracker = tracker()

        tracker.checkOrphanedSession()

        assertEquals(gameId, tracker.activeSession.value?.gameId)
        coVerify(exactly = 0) { sessionSaveFinalizer.finalize(any()) }
        coVerify(exactly = 0) { notices.orphanSaveRecovered(any(), any()) }
    }

    @Test
    fun `an orphan check whose emulator is reported gone recovers the save`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } returns SessionSaveOutcome.NothingToSave
        coEvery { preferencesRepository.getPersistedSession() } returns persisted
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns false
        val tracker = tracker()

        tracker.checkOrphanedSession()

        assertNull(tracker.activeSession.value)
        coVerify(exactly = 1) { sessionSaveFinalizer.finalize(any()) }
    }

    @Test
    fun `a matching session resumes when usage stats cannot answer`() = runTest {
        val tracker = trackerWithLiveSession()
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns null

        assertTrue(tracker.canResumeSession(gameId))
    }

    @Test
    fun `a matching session does not resume once usage stats report the emulator gone`() = runTest {
        val tracker = trackerWithLiveSession()
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns false

        assertFalse(tracker.canResumeSession(gameId))
    }

    @Test
    fun `an orphan recovery whose save work throws posts no notice`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } throws IOException("db")
        coEvery { preferencesRepository.getPersistedSession() } returns persisted

        tracker().endSession(stopService = false)

        coVerify(exactly = 0) { notices.orphanSaveRecovered(any(), any()) }
        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
    }
}
