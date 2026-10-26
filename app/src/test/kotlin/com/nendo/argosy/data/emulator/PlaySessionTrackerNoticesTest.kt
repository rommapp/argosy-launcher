package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.sync.SaveRecoveryGate
import com.nendo.argosy.domain.usecase.save.SyncSaveOnSessionEndUseCase
import com.nendo.argosy.util.PermissionHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PlaySessionTrackerNoticesTest {
    private val gameId = 7L
    private val emulatorPackage = "test.emulator"

    private val gameDao: GameDao = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val permissionHelper: PermissionHelper = mockk(relaxed = true)
    private val sessionSaveFinalizer: SessionSaveFinalizer = mockk()
    private val notices: SessionEndNotices = mockk(relaxed = true)

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
        sessionRecapFactsRecorder = mockk(relaxed = true)
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
    fun `a live session end whose save work throws posts no notice`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } throws IOException("db")
        val tracker = trackerWithLiveSession()

        tracker.endSession(stopService = false)

        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
        coVerify(exactly = 0) { notices.sessionSaveSynced(any(), any()) }
        coVerify(exactly = 0) { notices.orphanSaveRecovered(any(), any()) }
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
    fun `an orphan recovery whose save work throws posts no notice`() = runTest {
        coEvery { sessionSaveFinalizer.finalize(any()) } throws IOException("db")
        coEvery { preferencesRepository.getPersistedSession() } returns persisted

        tracker().endSession(stopService = false)

        coVerify(exactly = 0) { notices.orphanSaveRecovered(any(), any()) }
        coVerify(exactly = 0) { notices.sessionSaveNotKept(any()) }
    }
}
