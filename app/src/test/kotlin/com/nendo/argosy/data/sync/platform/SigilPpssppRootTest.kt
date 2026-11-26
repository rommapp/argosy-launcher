package com.nendo.argosy.data.sync.platform

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class SigilPpssppRootTest {

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val saveConfigRepo = mockk<EmulatorSaveConfigRepository>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)

    private val handler = SigilSaveHandler(
        context = context,
        gameDao = gameDao,
        emulatorResolver = emulatorResolver,
        emulatorSaveConfigRepository = saveConfigRepo,
        switchProfileParser = mockk(relaxed = true),
        titleDbRepository = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        fal = fal,
        connectionManager = { mockk<RomMConnectionManager>(relaxed = true) },
        saveUnitResolver = mockk(relaxed = true),
        builtinSaveBase = mockk(relaxed = true),
        retroArchPathResolver = mockk(relaxed = true),
        sigilSyncStateDao = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        cartFeatureScanner = mockk(relaxed = true),
        saveLocator = mockk(relaxed = true)
    )

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File(INTERNAL)
        every { context.filesDir } returns File(FILES_DIR)
        every { fal.externalStorageRoots() } returns listOf(INTERNAL, SD)
        every { fal.isDirectory(INTERNAL) } returns true
        every { fal.isDirectory(SD) } returns true
        coEvery { gameDao.getById(GAME_ID) } returns GameEntity(
            id = GAME_ID,
            title = "Game",
            sortTitle = "game",
            platformId = 1L,
            platformSlug = "psp",
            rommId = 7L,
            igdbId = null,
            localPath = "/roms/game.iso",
            source = GameSource.ROMM_SYNCED
        )
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns PPSSPP_PKG
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
    }

    private fun stored(user: String?, evaluated: String?) {
        coEvery { saveConfigRepo.resolveUserSavePath("ppsspp", "psp", any()) } returns user
        coEvery { saveConfigRepo.resolveEvaluatedSavePath("ppsspp", any()) } returns evaluated
        coEvery { saveConfigRepo.resolveEffectiveSavePath("ppsspp", "psp", any()) } returns (user ?: evaluated)
    }

    private fun liveConfigOnSd() {
        every { fal.exists("$SD/PSP/SYSTEM/ppsspp.ini") } returns true
        every { fal.lastModified("$SD/PSP/SYSTEM/ppsspp.ini") } returns 100L
    }

    @Test
    fun `the standalone ppsspp root follows the live stick over a stale evaluated folder`() = runTest {
        stored(user = null, evaluated = "$INTERNAL/PSP/SAVEDATA")
        liveConfigOnSd()

        val route = handler.localRoute(GAME_ID, "ppsspp")

        assertEquals("ppsspp_standalone", route?.layout)
        assertEquals(SD, route?.root)
    }

    @Test
    fun `a user ppsspp folder outranks the live stick`() = runTest {
        stored(user = "$INTERNAL/PSP/SAVEDATA", evaluated = null)
        liveConfigOnSd()

        assertEquals(INTERNAL, handler.localRoute(GAME_ID, "ppsspp")?.root)
    }

    @Test
    fun `with no ppsspp config the evaluated folder still decides the root`() = runTest {
        stored(user = null, evaluated = "$INTERNAL/PSP/SAVEDATA")

        assertEquals(INTERNAL, handler.localRoute(GAME_ID, "ppsspp")?.root)
    }

    private companion object {
        const val GAME_ID = 9L
        const val PPSSPP_PKG = "org.ppsspp.ppsspp"
        const val INTERNAL = "/storage/emulated/0"
        const val SD = "/storage/75D7-DC5F"
        const val FILES_DIR = "/data/user/0/com.nendo.argosy/files"
    }
}
