package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.StateTombstoneEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StateCacheManagerForeignSerialTest {

    private val stateCacheDao = mockk<StateCacheDao>(relaxed = true)
    private val stateTombstoneDao = mockk<StateTombstoneDao>(relaxed = true)
    private lateinit var manager: StateCacheManager

    private val own = row(1L, "duckstation", "SLUS-00001_1.sav")
    private val ownResume = row(2L, "duckstation", "SLUS-00001_resume.sav")
    private val foreign = row(3L, "duckstation", "SCUS-94163_2.sav", rommSaveId = 900L)
    private val foreignGlobal = row(4L, "duckstation", "savestate_3.sav")
    private val fromServer = row(5L, "duckstation", "Game [2026-01-01_10-00-00].state4", rommSaveId = 901L)
    private val romNamed = row(6L, "retroarch", "SCUS-94163_2.sav")
    private val foreignPpsspp = row(7L, "ppsspp", "NPJH50804_1.01_3.ppst")

    @Before
    fun setUp() {
        coEvery { stateCacheDao.getByGame(GAME_ID, any()) } returns
            listOf(own, ownResume, foreign, foreignGlobal, fromServer, romNamed, foreignPpsspp)
        coEvery { stateCacheDao.detachFromGame(any()) } returns 1
        manager = StateCacheManager(
            context = mockk<Context>(relaxed = true),
            gameDao = mockk(relaxed = true),
            stateCacheDao = stateCacheDao,
            stateTombstoneDao = stateTombstoneDao,
            saveCacheDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true) { coEvery { getRommUserId() } returns null },
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = mockk(relaxed = true),
            saveSyncApiClient = mockk(relaxed = true),
            payloadCodec = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            stateOwnershipTracker = mockk(relaxed = true)
        )
    }

    @Test
    fun `rows carrying another serial are detached, never deleted`() = runTest {
        val detached = manager.detachForeignSerialStates(GAME_ID, setOf("SLUS-00001", "ULUS10064"))

        assertEquals(3, detached)
        coVerify { stateCacheDao.detachFromGame(foreign.id) }
        coVerify { stateCacheDao.detachFromGame(foreignGlobal.id) }
        coVerify { stateCacheDao.detachFromGame(foreignPpsspp.id) }
        coVerify(exactly = 0) { stateCacheDao.detachFromGame(own.id) }
        coVerify(exactly = 0) { stateCacheDao.detachFromGame(ownResume.id) }
        coVerify(exactly = 0) { stateCacheDao.deleteById(any()) }
        coVerify(exactly = 0) { stateCacheDao.deleteByIds(any()) }
    }

    @Test
    fun `a name with no serial and a rom-named emulator's row stay attached`() = runTest {
        manager.detachForeignSerialStates(GAME_ID, setOf("SLUS-00001"))

        coVerify(exactly = 0) { stateCacheDao.detachFromGame(fromServer.id) }
        coVerify(exactly = 0) { stateCacheDao.detachFromGame(romNamed.id) }
    }

    @Test
    fun `a detached row's server copy is tombstoned for the game`() = runTest {
        manager.detachForeignSerialStates(GAME_ID, setOf("SLUS-00001"))

        coVerify { stateTombstoneDao.insert(match<StateTombstoneEntity> { it.gameId == GAME_ID && it.rommSaveId == 900L }) }
        coVerify(exactly = 0) { stateTombstoneDao.insert(match<StateTombstoneEntity> { it.rommSaveId == 901L }) }
    }

    @Test
    fun `without a trusted serial nothing is detached`() = runTest {
        assertEquals(0, manager.detachForeignSerialStates(GAME_ID, emptySet()))

        coVerify(exactly = 0) { stateCacheDao.detachFromGame(any()) }
        coVerify(exactly = 0) { stateTombstoneDao.insert(any()) }
    }

    private fun row(id: Long, emulatorId: String, fileName: String, rommSaveId: Long? = null) = StateCacheEntity(
        id = id,
        gameId = GAME_ID,
        platformSlug = "psx",
        emulatorId = emulatorId,
        slotNumber = id.toInt(),
        cachedAt = Instant.EPOCH,
        stateSize = 1L,
        cachePath = "psx/$GAME_ID/default/unknown/$fileName",
        rommSaveId = rommSaveId
    )

    private companion object {
        const val GAME_ID = 12L
    }
}
