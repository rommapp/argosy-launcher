package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.VersionGroups
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * A collection names the rom ids the server holds, but sibling consolidation keeps one row per
 * group. Resolving a collection entry only by rom id loses every absorbed member, which drops
 * the game from the collection even though its files are in the library.
 */
class CollectionAbsorbedRomTest {

    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var service: RomMCollectionSyncService

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        service = RomMCollectionSyncService(
            connectionManager = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            gameDao = gameDao,
            gameFileDao = gameFileDao,
            collectionDao = mockk(relaxed = true),
            collectionMembershipDao = mockk(relaxed = true),
            overlayWriter = mockk(relaxed = true),
            syncCoordinator = mockk(relaxed = true)
        )
    }

    @Test
    fun `a rom with its own row resolves directly`() = runBlocking {
        coEvery { gameDao.getByRommId(535L) } returns gameRow(id = 90L)

        assertEquals(90L, service.resolveCollectionGameId(535L))
    }

    @Test
    fun `an absorbed rom resolves to the game that swallowed it`() = runBlocking {
        coEvery { gameDao.getByRommId(536L) } returns null
        coEvery {
            gameFileDao.getGameIdForVersionGroup(VersionGroups.groupKey(536L))
        } returns 90L

        assertEquals(90L, service.resolveCollectionGameId(536L))
    }

    @Test
    fun `a rom that is genuinely absent stays unresolved`() = runBlocking {
        coEvery { gameDao.getByRommId(999L) } returns null
        coEvery { gameFileDao.getGameIdForVersionGroup(any()) } returns null

        assertNull(service.resolveCollectionGameId(999L))
    }

    private fun gameRow(id: Long): GameEntity = mockk(relaxed = true) {
        every { this@mockk.id } returns id
    }
}
