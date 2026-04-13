package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.libretro.NetplaySupportLevel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetplayPreflightCheckerTest {

    private val supportedDescriptor = CoreDescriptor(
        coreId = "snes9x",
        netplaySupport = NetplaySupportLevel.SUPPORTED,
        supportedPlatforms = setOf("snes")
    )

    private val unsupportedDescriptor = CoreDescriptor(
        coreId = "mupen",
        netplaySupport = NetplaySupportLevel.UNSUPPORTED,
        supportedPlatforms = setOf("n64")
    )

    private fun session(
        coreId: String = "snes9x",
        igdb: Int? = 1234,
        romHash: String = "rom-hash-match",
        coreHash: String = "core-hash-match"
    ) = NetplaySession(
        sessionId = "session-1",
        gameIgdbId = igdb,
        gameTitle = "Super Mario World",
        coreId = coreId,
        romHashPrefix = romHash,
        coreHash = coreHash,
        joinable = true,
        protocolVersion = 1
    )

    private fun game(id: Long = 10L, platformSlug: String = "snes"): GameEntity = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = platformSlug,
        title = "Super Mario World",
        sortTitle = "super mario world",
        localPath = null,
        rommId = null,
        igdbId = 1234L,
        source = GameSource.ROMM_SYNCED
    )

    private fun file(
        id: Long,
        gameId: Long,
        localPath: String? = "/tmp/rom$id.sfc",
        romHashPrefix: String? = null
    ): GameFileEntity = GameFileEntity(
        id = id,
        gameId = gameId,
        rommFileId = null,
        romId = 0L,
        fileName = "rom$id.sfc",
        filePath = "rom$id.sfc",
        category = "rom",
        fileSize = 1024L,
        localPath = localPath,
        romHashPrefix = romHashPrefix
    )

    private fun checker(
        gameDao: GameDao,
        gameFileDao: GameFileDao,
        coreHashLookup: CoreHashLookup = CoreHashLookup { "core-hash-match" },
        romHashProvider: RomHashProvider = RomHashProvider { null },
        coreRegistry: CoreRegistryAdapter = CoreRegistryAdapter { coreId ->
            when (coreId) {
                "snes9x" -> supportedDescriptor
                "mupen" -> unsupportedDescriptor
                else -> null
            }
        }
    ) = NetplayPreflightChecker(
        gameDao = gameDao,
        gameFileDao = gameFileDao,
        coreHashLookup = coreHashLookup,
        romHashProvider = romHashProvider,
        coreRegistry = coreRegistry
    )

    @Test
    fun `unknown core returns CoreNotSupported`() = runTest {
        val gameDao = mockk<GameDao>()
        val fileDao = mockk<GameFileDao>()
        val result = checker(gameDao, fileDao).check(session(coreId = "unknown"))
        assertEquals(NetplayPreflightResult.CoreNotSupported, result)
    }

    @Test
    fun `unsupported core returns CoreNotSupported`() = runTest {
        val gameDao = mockk<GameDao>()
        val fileDao = mockk<GameFileDao>()
        val result = checker(gameDao, fileDao).check(session(coreId = "mupen"))
        assertEquals(NetplayPreflightResult.CoreNotSupported, result)
    }

    @Test
    fun `missing igdb returns RomNotFound`() = runTest {
        val gameDao = mockk<GameDao>()
        val fileDao = mockk<GameFileDao>()
        val result = checker(gameDao, fileDao).check(session(igdb = null))
        assertEquals(NetplayPreflightResult.RomNotFound, result)
    }

    @Test
    fun `game not in library returns RomNotFound`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns null
        }
        val fileDao = mockk<GameFileDao>()
        val result = checker(gameDao, fileDao).check(session())
        assertEquals(NetplayPreflightResult.RomNotFound, result)
    }

    @Test
    fun `no files returns RomNotFound`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns emptyList()
        }
        val result = checker(gameDao, fileDao).check(session())
        assertEquals(NetplayPreflightResult.RomNotFound, result)
    }

    @Test
    fun `hash mismatch on cached hash returns RomVersionMismatch`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 1, gameId = 10L, romHashPrefix = "different-hash")
            )
        }
        val result = checker(gameDao, fileDao).check(session())
        assertEquals(NetplayPreflightResult.RomVersionMismatch, result)
    }

    @Test
    fun `joinable when cached hash matches and core hash matches`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 1, gameId = 10L, romHashPrefix = "rom-hash-match")
            )
        }
        val result = checker(gameDao, fileDao).check(session())
        assertTrue(result is NetplayPreflightResult.Joinable)
        assertEquals("/tmp/rom1.sfc", (result as NetplayPreflightResult.Joinable).localFilePath)
    }

    @Test
    fun `on-demand hash computation persists and matches`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao>(relaxUnitFun = true) {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 5, gameId = 10L, romHashPrefix = null)
            )
        }
        val fakeHasher = object : RomHashProvider {
            var called = 0
            override fun computeRomHashPrefix(file: File): String? {
                called++
                return "rom-hash-match"
            }
        }
        val result = checker(gameDao, fileDao, romHashProvider = fakeHasher).check(session())
        assertTrue(result is NetplayPreflightResult.Joinable)
        assertEquals(1, fakeHasher.called)
        coVerify { fileDao.updateRomHashPrefix(5, "rom-hash-match") }
    }

    @Test
    fun `core hash mismatch returns CoreVersionMismatch`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 1, gameId = 10L, romHashPrefix = "rom-hash-match")
            )
        }
        val result = checker(
            gameDao = gameDao,
            gameFileDao = fileDao,
            coreHashLookup = CoreHashLookup { "different-core-hash" }
        ).check(session())
        assertTrue(result is NetplayPreflightResult.Joinable)
    }

    @Test
    fun `missing local core hash returns CoreVersionMismatch`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 1, gameId = 10L, romHashPrefix = "rom-hash-match")
            )
        }
        val result = checker(
            gameDao = gameDao,
            gameFileDao = fileDao,
            coreHashLookup = CoreHashLookup { null }
        ).check(session())
        assertTrue(result is NetplayPreflightResult.Joinable)
    }

    @Test
    fun `uncomputable hash falls through to RomNotFound`() = runTest {
        val gameDao = mockk<GameDao> {
            coEvery { getByIgdbId(1234L) } returns game()
        }
        val fileDao = mockk<GameFileDao> {
            coEvery { getFilesForGame(10L) } returns listOf(
                file(id = 1, gameId = 10L, romHashPrefix = null)
            )
        }
        val result = checker(
            gameDao = gameDao,
            gameFileDao = fileDao,
            romHashProvider = RomHashProvider { null }
        ).check(session())
        assertEquals(NetplayPreflightResult.RomNotFound, result)
    }
}

private fun CoreHashLookup(lookup: (String) -> String?) = object : CoreHashLookup {
    override fun hashForCoreId(coreId: String): String? = lookup(coreId)
}

private fun RomHashProvider(compute: (File) -> String?) = object : RomHashProvider {
    override fun computeRomHashPrefix(file: File): String? = compute(file)
}

private fun CoreRegistryAdapter(find: (String) -> CoreDescriptor?) = object : CoreRegistryAdapter {
    override fun findCore(coreId: String): CoreDescriptor? = find(coreId)
}
