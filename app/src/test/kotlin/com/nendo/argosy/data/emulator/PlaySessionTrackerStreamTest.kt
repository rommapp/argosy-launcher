package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.streaming.StreamClaim
import com.nendo.argosy.data.streaming.StreamClaimReleaser
import com.nendo.argosy.data.streaming.StreamReleaseOutcome
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.data.sync.SaveRecoveryGate
import com.nendo.argosy.domain.usecase.state.SyncStatesOnSessionEndUseCase
import com.nendo.argosy.util.PermissionHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class PlaySessionTrackerStreamTest {
    private val gameId = 11L
    private val claim = StreamClaim(platform = "psx", containerKey = "box-1", claimedAt = "2026-11-13T09:00:00+00:00")

    private val gameDao: GameDao = mockk(relaxed = true)
    private val playSessionDao: PlaySessionDao = mockk(relaxed = true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val permissionHelper: PermissionHelper = mockk(relaxed = true)
    private val sessionSaveFinalizer: SessionSaveFinalizer = mockk(relaxed = true)
    private val stateSync: SyncStatesOnSessionEndUseCase = mockk(relaxed = true)
    private val releaser: StreamClaimReleaser = mockk()

    private val persistedStream = PersistedSession(
        gameId = gameId,
        emulatorPackage = StreamSessionManager.STREAM_PACKAGE,
        startTime = Instant.parse("2026-11-13T09:00:05Z"),
        coreName = null,
        isHardcore = false,
        streamClaim = claim
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(any()) } returns null
        every { preferencesRepository.userPreferences } returns flowOf(UserPreferences())
        coEvery { preferencesRepository.getPersistedSession() } returns null
        coEvery { releaser.release(any()) } returns StreamReleaseOutcome.RELEASED
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
        playSessionDao = playSessionDao,
        syncStatesOnSessionEndUseCase = dagger.Lazy { stateSync },
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
        sessionEndNotices = mockk(relaxed = true),
        snapshotRouter = lazyMock(),
        sessionRecapFactsRecorder = mockk(relaxed = true),
        sessionScreenshotScanner = mockk(relaxed = true),
        streamClaimReleaser = dagger.Lazy { releaser }
    )

    private fun assertNoLocalSessionWork() {
        coVerify(exactly = 0) { sessionSaveFinalizer.finalize(any()) }
        coVerify(exactly = 0) { playSessionDao.insert(any()) }
        coVerify(exactly = 0) { stateSync.invoke(any(), any(), any()) }
    }

    @Test
    fun `an orphaned stream releases its claim, records nothing and clears the session`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persistedStream
        val tracker = tracker()

        tracker.checkOrphanedSession()

        coVerify(exactly = 1) { releaser.release(claim) }
        coVerify(exactly = 1) { preferencesRepository.clearActiveSession(persistedStream.startTime) }
        verify(exactly = 0) { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) }
        assertNull(tracker.activeSession.value)
        assertNoLocalSessionWork()
    }

    @Test
    fun `an orphaned stream RomM already ended is cleared the same way`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persistedStream
        coEvery { releaser.release(claim) } returns StreamReleaseOutcome.ALREADY_ENDED

        tracker().checkOrphanedSession()

        coVerify(exactly = 1) { preferencesRepository.clearActiveSession(persistedStream.startTime) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `an orphaned stream whose release fails is still cleared`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persistedStream
        coEvery { releaser.release(claim) } returns StreamReleaseOutcome.FAILED

        tracker().checkOrphanedSession()

        coVerify(exactly = 1) { preferencesRepository.clearActiveSession(persistedStream.startTime) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `an orphaned stream with no stored claim is cleared without a release`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persistedStream.copy(streamClaim = null)

        tracker().checkOrphanedSession()

        coVerify(exactly = 0) { releaser.release(any()) }
        coVerify(exactly = 1) { preferencesRepository.clearActiveSession(persistedStream.startTime) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `a session end that finds only a persisted stream releases it and reports skipped`() = runTest {
        coEvery { preferencesRepository.getPersistedSession() } returns persistedStream

        val result = tracker().endSession(stopService = false)

        assertEquals(SessionEndResult.Skipped, result)
        coVerify(exactly = 1) { releaser.release(claim) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `a stream session round trip persists the claim and clears it, recording nothing`() = runTest {
        val tracker = tracker()

        assertTrue(tracker.startStreamSession(gameId, claim))
        val started = tracker.activeSession.value
        assertTrue(started?.isStream == true)
        assertEquals(claim, started?.streamClaim)
        coVerify(timeout = TIMEOUT_MS) {
            preferencesRepository.persistActiveSession(
                gameId = gameId,
                emulatorPackage = StreamSessionManager.STREAM_PACKAGE,
                startTime = started!!.startTime,
                coreName = null,
                isHardcore = false,
                channelName = any(),
                variantFileId = any(),
                origin = any(),
                isNetplayGuest = any(),
                streamClaim = claim
            )
        }

        tracker.endStreamSession(claim)

        assertNull(tracker.activeSession.value)
        coVerify(timeout = TIMEOUT_MS) { preferencesRepository.clearActiveSession(started!!.startTime) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `ending a different claim leaves the stream session open`() = runTest {
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)

        tracker.endStreamSession(claim.copy(claimedAt = "2026-11-13T10:00:00+00:00"))

        assertEquals(claim, tracker.activeSession.value?.streamClaim)
    }

    @Test
    fun `a session end over a live stream session ends it without play time or save sync`() = runTest {
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)
        val startTime = tracker.activeSession.value!!.startTime

        val result = tracker.endSession(stopService = false)

        assertEquals(SessionEndResult.Skipped, result)
        assertNull(tracker.activeSession.value)
        coVerify(exactly = 1) { preferencesRepository.clearActiveSession(startTime) }
        coVerify(exactly = 0) { releaser.release(any()) }
        assertNoLocalSessionWork()
    }

    @Test
    fun `cancelling a stream session runs no state sync`() = runTest {
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)

        tracker.cancelSession()

        assertNull(tracker.activeSession.value)
        assertNoLocalSessionWork()
    }

    @Test
    fun `a stream session is never resumed as a local game`() = runTest {
        every { permissionHelper.isPackageOnScreenOrRecent(any(), any(), any()) } returns null
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)

        assertFalse(tracker.canResumeSession(gameId))
    }

    @Test
    fun `a stream session caches nothing for a quit`() = runTest {
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)

        assertNull(tracker.cacheCurrentSessionForQuit())
        tracker.cacheCurrentSessionStatesForQuit()

        coVerify(exactly = 0) { stateSync.invoke(any(), any(), any()) }
    }

    @Test
    fun `a stream session is refused while another session is live`() = runTest {
        val tracker = tracker()
        tracker.startStreamSession(gameId, claim)

        assertFalse(tracker.startStreamSession(gameId + 1, claim.copy(containerKey = "box-2")))
        assertEquals(claim, tracker.activeSession.value?.streamClaim)
    }

    private companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
