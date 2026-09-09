package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameLocalPathInfo
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.DeepLinkRequest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveDeepLinkGameUseCaseTest {

    private lateinit var gameDao: GameDao
    private lateinit var useCase: ResolveDeepLinkGameUseCase

    private fun game(id: Long): GameEntity = mockk<GameEntity>(relaxed = true).also {
        every { it.id } returns id
    }

    private fun pathInfo(id: Long, path: String) = GameLocalPathInfo(
        id = id,
        platformId = 8L,
        platformSlug = "snes",
        source = GameSource.ROMM_SYNCED,
        localPath = path
    )

    @Before
    fun setUp() {
        gameDao = mockk()
        useCase = ResolveDeepLinkGameUseCase(gameDao)
    }

    @Test
    fun `resolves by game id when the game exists`() = runTest {
        coEvery { gameDao.getById(42L) } returns game(42L)

        val result = useCase(DeepLinkRequest(gameId = 42L))

        assertEquals(DeepLinkResolution.Resolved(42L), result)
    }

    @Test
    fun `reports not found when the game id does not exist`() = runTest {
        coEvery { gameDao.getById(42L) } returns null

        val result = useCase(DeepLinkRequest(gameId = 42L))

        assertTrue(result is DeepLinkResolution.NotFound)
    }

    @Test
    fun `resolves by romm id`() = runTest {
        coEvery { gameDao.getByRommId(1234L) } returns game(7L)

        val result = useCase(DeepLinkRequest(rommId = 1234L))

        assertEquals(DeepLinkResolution.Resolved(7L), result)
    }

    @Test
    fun `resolves by exact rom path`() = runTest {
        val path = "/storage/emulated/0/ROMs/snes/Some Game.zip"
        coEvery { gameDao.getByPath(path) } returns game(9L)

        val result = useCase(DeepLinkRequest(romPath = path))

        assertEquals(DeepLinkResolution.Resolved(9L), result)
    }

    @Test
    fun `falls back to file name when the stored path differs`() = runTest {
        val requested = "/mnt/other/root/snes/Some Game.zip"
        coEvery { gameDao.getByPath(requested) } returns null
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(
            pathInfo(3L, "/storage/emulated/0/ROMs/snes/Another Game.zip"),
            pathInfo(11L, "/storage/emulated/0/ROMs/snes/Some Game.zip")
        )

        val result = useCase(DeepLinkRequest(romPath = requested))

        assertEquals(DeepLinkResolution.Resolved(11L), result)
    }

    @Test
    fun `refuses to guess when a file name matches more than one game`() = runTest {
        val requested = "/mnt/other/root/snes/Some Game.zip"
        coEvery { gameDao.getByPath(requested) } returns null
        coEvery { gameDao.getGamesWithLocalPathInfo() } returns listOf(
            pathInfo(3L, "/storage/emulated/0/ROMs/snes/Some Game.zip"),
            pathInfo(11L, "/storage/emulated/0/ROMs/nes/Some Game.zip")
        )

        val result = useCase(DeepLinkRequest(romPath = requested))

        assertTrue(result is DeepLinkResolution.Ambiguous)
    }

    @Test
    fun `reports not found when the request carries no identifier`() = runTest {
        val result = useCase(DeepLinkRequest())

        assertTrue(result is DeepLinkResolution.NotFound)
    }
}
