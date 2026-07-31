package com.nendo.argosy.libretro

import com.nendo.argosy.data.cheats.CheatsRepository
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheatSessionManagerNetplayTest {

    private fun cheatEntity(id: Long, enabled: Boolean, index: Int = id.toInt()): CheatEntity {
        return CheatEntity(
            id = id,
            gameId = 1L,
            cheatIndex = index,
            description = "c$id",
            code = "00:00",
            enabled = enabled,
            isUserCreated = false,
            lastUsedAt = 0L
        )
    }

    private fun buildManager(
        scope: CoroutineScope,
        initialCheats: List<CheatEntity>,
        canSerialize: Boolean = true
    ): Triple<CheatSessionManager, CheatDao, CheatsRepository> {
        val cheatDao = mockk<CheatDao>(relaxed = true)
        val gameDao = mockk<GameDao>(relaxed = true)
        val repository = mockk<CheatsRepository>(relaxed = true)
        coEvery { repository.getCheatsForGame(any()) } returns initialCheats andThen initialCheats.map { it.copy(enabled = false) }
        coEvery { repository.getVariantsForGame(any()) } returns emptyList()
        coEvery { repository.getSelectedVariant(any()) } returns null
        coEvery { repository.isConfigured() } returns false
        coEvery { gameDao.getById(any()) } returns null
        val manager = CheatSessionManager(
            gameId = 1L,
            cheatDao = cheatDao,
            gameDao = gameDao,
            cheatsRepository = repository,
            canSerialize = { canSerialize },
            scope = scope
        )
        manager.loadCheats(hardcoreMode = false)
        return Triple(manager, cheatDao, repository)
    }

    @Test
    fun disableAllAndCycleDisablesEnabledCheatsAndCyclesState() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val view = mockk<GLRetroView>(relaxed = true)
        every { view.serializeState() } returns byteArrayOf(1, 2, 3)
        every { view.resetCheat() } just Runs
        every { view.unserializeState(any()) } returns true

        val enabled = cheatEntity(1L, enabled = true)
        val disabled = cheatEntity(2L, enabled = false)
        val (manager, cheatDao, _) = buildManager(scope, listOf(enabled, disabled))
        manager.setRetroView(view)
        advanceUntilIdle()
        assertTrue(manager.hasAnyEnabledCheats)

        manager.disableAllAndCycleForNetplay()

        coVerify(exactly = 1) { cheatDao.setEnabled(1L, false, any()) }
        coVerify(exactly = 0) { cheatDao.setEnabled(2L, any(), any()) }
        verify(exactly = 1) { view.serializeState() }
        verify(exactly = 1) { view.resetCheat() }
        verify(exactly = 1) { view.unserializeState(any()) }
        assertFalse(manager.hasAnyEnabledCheats)
    }

    @Test
    fun disableAllAndCycleSkipsTheStateRoundTripWhenTheCoreCannotSerialize() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val view = mockk<GLRetroView>(relaxed = true)
        every { view.resetCheat() } just Runs

        val enabled = cheatEntity(1L, enabled = true)
        val (manager, _, _) = buildManager(scope, listOf(enabled), canSerialize = false)
        manager.setRetroView(view)
        advanceUntilIdle()

        manager.disableAllAndCycleForNetplay()

        verify(exactly = 0) { view.serializeState() }
        verify(exactly = 0) { view.unserializeState(any()) }
        verify(exactly = 1) { view.resetCheat() }
        assertFalse(manager.hasAnyEnabledCheats)
    }

    @Test
    fun disableAllAndCycleIsNoOpWhenNothingEnabled() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val view = mockk<GLRetroView>(relaxed = true)

        val (manager, cheatDao, _) = buildManager(scope, listOf(cheatEntity(1L, enabled = false)))
        manager.setRetroView(view)
        advanceUntilIdle()
        assertFalse(manager.hasAnyEnabledCheats)

        manager.disableAllAndCycleForNetplay()

        coVerify(exactly = 0) { cheatDao.setEnabled(any(), any(), any()) }
        verify(exactly = 0) { view.serializeState() }
        verify(exactly = 0) { view.resetCheat() }
    }
}
