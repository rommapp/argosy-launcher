package com.nendo.argosy.libretro

import android.content.Context
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.ra.RAAchievementPatch
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.RAAwardResult
import com.nendo.argosy.data.repository.RAPatchFetchResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.achievement.HashVerification
import com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
import com.nendo.argosy.hardware.AmbientLedManager
import com.swordfish.libretrodroid.AchievementDef
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetroAchievementsSessionManagerTest {

    private class Harness(
        val manager: RetroAchievementsSessionManager,
        val raRepository: RetroAchievementsRepository,
        val achievementDao: AchievementDao,
        val runtime: AchievementRuntime,
        val updateBus: AchievementUpdateBus,
        private val armed: CapturingSlot<Array<AchievementDef>>
    ) {
        val armedIds: Set<Long>?
            get() = if (armed.isCaptured) armed.captured.map { it.id }.toSet() else null
    }

    private fun game(raId: Long?) = GameEntity(
        id = 1L,
        platformId = 1L,
        title = "Test Game",
        sortTitle = "test game",
        localPath = "/roms/test.gba",
        rommId = 100L,
        igdbId = null,
        raId = raId,
        source = GameSource.ROMM_SYNCED
    )

    private fun patch(vararg achievements: RAAchievementPatch) = RAPatchData(
        id = 4242L,
        consoleId = 5,
        achievements = achievements.toList()
    )

    private fun achievement(id: Long, title: String = "Achievement $id", memAddr: String = "0xH00$id=1") =
        RAAchievementPatch(id = id, memAddr = memAddr, title = title, points = 5)

    private fun storedRow(raId: Long, unlockedAt: Long? = null, unlockedHardcoreAt: Long? = null) =
        AchievementEntity(
            gameId = 1L,
            raId = raId,
            title = "Achievement $raId",
            description = null,
            points = 5,
            type = null,
            badgeUrl = null,
            badgeUrlLock = null,
            unlockedAt = unlockedAt,
            unlockedHardcoreAt = unlockedHardcoreAt
        )

    private fun build(
        scope: CoroutineScope,
        requestedHardcore: Boolean,
        loggedIn: Boolean = true,
        raId: Long? = 4242L,
        startSucceeds: Boolean = true,
        hardcoreUnlocks: Set<Long> = emptySet(),
        casualUnlocks: Set<Long> = emptySet(),
        liveFetch: RAPatchFetchResult = RAPatchFetchResult.Unavailable,
        cachedPatch: RAPatchData? = null,
        storedRows: List<AchievementEntity> = emptyList(),
        matchedHash: String? = "0123456789abcdef0123456789abcdef"
    ): Harness {
        val gameDao = mockk<GameDao>(relaxed = true)
        val raRepository = mockk<RetroAchievementsRepository>(relaxed = true)
        val achievementDao = mockk<AchievementDao>(relaxed = true)
        val runtime = mockk<AchievementRuntime>(relaxed = true)
        val updateBus = mockk<AchievementUpdateBus>(relaxed = true)
        val armed = slot<Array<AchievementDef>>()
        every { runtime.arm(capture(armed), any()) } just Runs
        val verifyRAGameIdUseCase = mockk<VerifyRAGameIdUseCase>()
        coEvery { raRepository.isLoggedIn() } returns loggedIn
        coEvery { gameDao.getById(1L) } returns game(raId)
        coEvery { verifyRAGameIdUseCase.verifyHash(any(), any()) } returns HashVerification(raId, matchedHash)
        coEvery { raRepository.startSession(any(), any(), any()) } returns
            RetroAchievementsRepository.RASessionResult(
                success = startSucceeds,
                hardcoreUnlocks = hardcoreUnlocks,
                allUnlocks = hardcoreUnlocks + casualUnlocks
            )
        coEvery { raRepository.fetchPatchData(any()) } returns liveFetch
        coEvery { raRepository.getCachedPatchData(any()) } returns cachedPatch
        coEvery { raRepository.getCachedAchievements(1L) } returns storedRows
        coEvery { raRepository.activeOwnerUserId() } returns 7L
        coEvery { raRepository.awardAchievement(any(), any(), any()) } returns RAAwardResult.Error("offline")
        val manager = RetroAchievementsSessionManager(
            gameId = 1L,
            romPath = "/roms/test.gba",
            requestedHardcore = requestedHardcore,
            gameDao = gameDao,
            overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true),
            achievementDao = achievementDao,
            raRepository = raRepository,
            verifyRAGameIdUseCase = verifyRAGameIdUseCase,
            achievementUpdateBus = updateBus,
            ambientLedManager = mockk<AmbientLedManager>(relaxed = true),
            socialRepository = mockk<SocialRepository>(relaxed = true),
            scope = scope,
            context = mockk<Context>(relaxed = true),
            achievementRuntime = runtime
        )
        return Harness(manager, raRepository, achievementDao, runtime, updateBus, armed)
    }

    @Test
    fun armingPublishesTheLoadedCountsForTheOtherScreen() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = false,
            startSucceeds = true,
            casualUnlocks = setOf(1L, 2L),
            liveFetch = RAPatchFetchResult.Fetched(patch(achievement(1L), achievement(2L), achievement(3L)))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        coVerify {
            harness.updateBus.emit(
                AchievementUpdateBus.AchievementUpdate(gameId = 1L, totalCount = 3, earnedCount = 2)
            )
        }
        scope.cancel()
    }

    @Test
    fun hardcoreRequestIsPendingUntilTheServerAnswers() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = true)

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))

        assertEquals(RASessionMode.PENDING, harness.manager.sessionMode.value)
        scope.cancel()
    }

    @Test
    fun hardcoreIsClaimedOnlyAfterAHardcoreStartSucceeds() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = true, startSucceeds = true)

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.HARDCORE, harness.manager.sessionMode.value)
        coVerify(exactly = 1) { harness.raRepository.startSession(4242L, true, "0123456789abcdef0123456789abcdef") }
        assertNull(harness.manager.raConnectionInfo)
        scope.cancel()
    }

    @Test
    fun hardcoreIsNotClaimedWithoutAMatchedHash() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = true,
            startSucceeds = true,
            matchedHash = null,
            hardcoreUnlocks = setOf(1L),
            casualUnlocks = setOf(2L),
            liveFetch = RAPatchFetchResult.Fetched(patch(achievement(1L), achievement(2L), achievement(3L)))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        coVerify(exactly = 1) { harness.raRepository.startSession(4242L, true, null) }
        assertEquals(setOf(3L), harness.armedIds)
        assertEquals(2, harness.manager.earnedAchievements)
        scope.cancel()
    }

    @Test
    fun aClaimedHardcoreSessionArmsAchievementsUnlockedOnlyInCasual() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = true,
            startSucceeds = true,
            hardcoreUnlocks = setOf(1L),
            casualUnlocks = setOf(2L),
            liveFetch = RAPatchFetchResult.Fetched(patch(achievement(1L), achievement(2L), achievement(3L)))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.HARDCORE, harness.manager.sessionMode.value)
        assertEquals(setOf(2L, 3L), harness.armedIds)
        assertEquals(1, harness.manager.earnedAchievements)
        scope.cancel()
    }

    @Test
    fun refusedHardcoreStartResolvesToCasualAndReportsTheFallback() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = true, startSucceeds = false)

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        assertFalse(harness.manager.raSessionActive)
        val info = harness.manager.raConnectionInfo
        assertNotNull(info)
        assertFalse(info!!.connected)
        assertFalse(info.isHardcore)
        scope.cancel()
    }

    @Test
    fun signedOutHardcoreRequestResolvesToCasualWithoutContactingTheServer() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = true, loggedIn = false)

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        coVerify(exactly = 0) { harness.raRepository.startSession(any(), any(), any()) }
        assertFalse(harness.manager.raConnectionInfo!!.connected)
        scope.cancel()
    }

    @Test
    fun hardcoreRequestForAGameWithoutAnRaIdResolvesToCasual() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = true, raId = null)

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        coVerify(exactly = 0) { harness.raRepository.startSession(any(), any(), any()) }
        assertFalse(harness.manager.raConnectionInfo!!.connected)
        scope.cancel()
    }

    @Test
    fun casualLaunchIsCasualFromTheStartAndShowsNoFallbackBanner() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(scope, requestedHardcore = false, startSucceeds = false)

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        assertNull(harness.manager.raConnectionInfo)
        scope.cancel()
    }

    @Test
    fun aFailedStartArmsFromTheCacheWithTheLivePathFilters() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = false,
            startSucceeds = false,
            liveFetch = RAPatchFetchResult.RetryLater,
            cachedPatch = patch(
                achievement(1L),
                achievement(2L),
                achievement(90L, title = "Unknown Emulator"),
                achievement(91L, title = "Emulator Warning: core"),
                achievement(92L, memAddr = " ")
            )
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(setOf(1L, 2L), harness.armedIds)
        verify { harness.runtime.arm(any(), 5) }
        assertEquals(2, harness.manager.totalAchievements)
        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        assertFalse(harness.manager.raSessionActive)
        scope.cancel()
    }

    @Test
    fun liveDefinitionsWinOverTheCacheWhenTheFetchSucceeds() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = false,
            startSucceeds = false,
            liveFetch = RAPatchFetchResult.Fetched(patch(achievement(3L))),
            cachedPatch = patch(achievement(1L), achievement(2L))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(setOf(3L), harness.armedIds)
        coVerify(exactly = 0) { harness.raRepository.getCachedPatchData(any()) }
        scope.cancel()
    }

    @Test
    fun offlineArmingSkipsEveryAchievementTheLocalDatabaseAlreadyHasUnlocked() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = false,
            startSucceeds = false,
            liveFetch = RAPatchFetchResult.RetryLater,
            cachedPatch = patch(achievement(1L), achievement(2L), achievement(3L)),
            storedRows = listOf(
                storedRow(1L, unlockedAt = 10L),
                storedRow(2L, unlockedHardcoreAt = 20L),
                storedRow(3L)
            )
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(setOf(3L), harness.armedIds)
        assertEquals(2, harness.manager.earnedAchievements)
        coVerify(exactly = 0) { harness.achievementDao.markUnlocked(any(), any(), any(), any()) }
        coVerify(exactly = 0) { harness.achievementDao.markUnlockedHardcore(any(), any(), any(), any()) }
        scope.cancel()
    }

    @Test
    fun aConfirmedHardcoreSessionArmedFromTheCacheRunsCasualAndAwardsCasual() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = true,
            startSucceeds = true,
            liveFetch = RAPatchFetchResult.RetryLater,
            cachedPatch = patch(achievement(1L))
        )
        val view = mockk<GLRetroView>(relaxed = true)
        val listeners = mutableListOf<((Long) -> Unit)?>()
        every { view.achievementUnlockListener = captureNullable(listeners) } just Runs

        harness.manager.initialize(view)
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        assertEquals(setOf(1L), harness.armedIds)
        assertFalse(harness.manager.raConnectionInfo!!.connected)

        listeners.filterNotNull().single().invoke(1L)
        runCurrent()

        coVerify(exactly = 1) { harness.raRepository.awardAchievement(1L, 1L, false) }
        coVerify(exactly = 0) { harness.raRepository.awardAchievement(any(), any(), true) }
        scope.cancel()
    }

    @Test
    fun aRefusedHardcoreStartArmedFromTheCacheNeverClaimsHardcore() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = true,
            startSucceeds = false,
            liveFetch = RAPatchFetchResult.RetryLater,
            cachedPatch = patch(achievement(1L))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.CASUAL, harness.manager.sessionMode.value)
        assertEquals(setOf(1L), harness.armedIds)
        assertFalse(harness.manager.raConnectionInfo!!.isHardcore)
        scope.cancel()
    }

    @Test
    fun aHardcoreSessionWithLiveDefinitionsStaysHardcore() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val harness = build(
            scope,
            requestedHardcore = true,
            startSucceeds = true,
            liveFetch = RAPatchFetchResult.Fetched(patch(achievement(1L))),
            cachedPatch = patch(achievement(1L))
        )

        harness.manager.initialize(mockk<GLRetroView>(relaxed = true))
        runCurrent()

        assertEquals(RASessionMode.HARDCORE, harness.manager.sessionMode.value)
        assertTrue(harness.manager.raConnectionInfo!!.isHardcore)
        assertEquals(setOf(1L), harness.armedIds)
        scope.cancel()
    }
}
