package com.nendo.argosy.libretro

import android.content.Context
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.GameUserOverlayWriter
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
import com.nendo.argosy.hardware.AmbientLedManager
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetroAchievementsSessionManagerTest {

    private class Harness(
        val manager: RetroAchievementsSessionManager,
        val raRepository: RetroAchievementsRepository
    )

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

    private fun build(
        scope: CoroutineScope,
        requestedHardcore: Boolean,
        loggedIn: Boolean = true,
        raId: Long? = 4242L,
        startSucceeds: Boolean = true
    ): Harness {
        val gameDao = mockk<GameDao>(relaxed = true)
        val raRepository = mockk<RetroAchievementsRepository>(relaxed = true)
        val verifyRAGameIdUseCase = mockk<VerifyRAGameIdUseCase>()
        coEvery { raRepository.isLoggedIn() } returns loggedIn
        coEvery { gameDao.getById(1L) } returns game(raId)
        coEvery { verifyRAGameIdUseCase.invoke(any(), any()) } returns null
        coEvery { raRepository.startSession(any(), any()) } returns
            RetroAchievementsRepository.RASessionResult(success = startSucceeds)
        coEvery { raRepository.getGamePatchData(any()) } returns null
        val manager = RetroAchievementsSessionManager(
            gameId = 1L,
            romPath = "/roms/test.gba",
            requestedHardcore = requestedHardcore,
            gameDao = gameDao,
            overlayWriter = mockk<GameUserOverlayWriter>(relaxed = true),
            achievementDao = mockk<AchievementDao>(relaxed = true),
            raRepository = raRepository,
            verifyRAGameIdUseCase = verifyRAGameIdUseCase,
            achievementUpdateBus = mockk<AchievementUpdateBus>(relaxed = true),
            ambientLedManager = mockk<AmbientLedManager>(relaxed = true),
            socialRepository = mockk<SocialRepository>(relaxed = true),
            scope = scope,
            context = mockk<Context>(relaxed = true)
        )
        return Harness(manager, raRepository)
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
        coVerify(exactly = 1) { harness.raRepository.startSession(4242L, true) }
        assertNull(harness.manager.raConnectionInfo)
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
        coVerify(exactly = 0) { harness.raRepository.startSession(any(), any()) }
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
        coVerify(exactly = 0) { harness.raRepository.startSession(any(), any()) }
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
}
