package com.nendo.argosy.data.steam

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.StorageVolumeDetector
import com.nendo.argosy.util.AppPaths
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The home screen asks whether every Steam game is installed on each refresh, and a negative
 * answer clears the stored install path. Storage that is merely unreadable - an unmounted
 * card, a permission not yet granted - must never reach that conclusion, because forgetting
 * where a game lives cannot be undone by looking again.
 */
class SteamInstallPresenceTest {

    private lateinit var tempDir: File
    private lateinit var resolver: SteamPathResolver

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>()
    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val storageVolumeDetector = mockk<StorageVolumeDetector>(relaxed = true)
    private val gnInstallProbe = mockk<GnInstallProbe>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var steamBase: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("steam_presence").toFile()
        steamBase = File(tempDir, "roms/steam")

        every { preferencesRepository.userPreferences } returns
            flowOf(UserPreferences(romStoragePath = File(tempDir, "roms").absolutePath))
        coEvery { platformDao.getById(any()) } returns null
        every { androidDataAccessor.exists(any()) } returns false
        every { storageVolumeDetector.detectStorageVolumes() } returns emptyList()

        resolver = SteamPathResolver(
            context = context,
            gameDao = gameDao,
            platformDao = platformDao,
            preferencesRepository = preferencesRepository,
            androidDataAccessor = androidDataAccessor,
            storageVolumeDetector = storageVolumeDetector,
            gnInstallProbe = gnInstallProbe
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun game(localPath: String?) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = "steam",
        title = "Hades",
        sortTitle = "hades",
        localPath = localPath,
        rommId = null,
        igdbId = null,
        steamAppId = 1145360L,
        steamLauncher = GameEntity.LAUNCHER_UNSPECIFIED,
        source = GameSource.STEAM
    )

    @Test
    fun `an unreachable volume does not count as a deleted install`() = runTest {
        val onMissingCard = File("/storage/ABCD-1234/roms/steam/Hades")

        val installed = resolver.isGameInstalled(game(onMissingCard.absolutePath))

        assertFalse(installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a reachable parent with the game gone clears the path`() = runTest {
        steamBase.mkdirs()
        val removed = File(steamBase, "Hades")

        val installed = resolver.isGameInstalled(game(removed.absolutePath))

        assertFalse(installed)
        coVerify(exactly = 1) { gameDao.update(match { it.localPath == null }) }
    }

    @Test
    fun `a present directory missing its marker is left alone`() = runTest {
        val present = File(steamBase, "Hades").apply { mkdirs() }

        val installed = resolver.isGameInstalled(game(present.absolutePath))

        assertFalse("no completion marker means not installed", installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a download still in staging keeps its path`() = runTest {
        val staging = File(tempDir, "${AppPaths.STEAM_STAGING_DIR}/1145360")

        val installed = resolver.isGameInstalled(game(staging.absolutePath))

        assertFalse(installed)
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `a completed install reports installed and is not cleared`() = runTest {
        val dir = File(steamBase, "Hades").apply { mkdirs() }
        File(dir, ".download_complete").createNewFile()

        val installed = resolver.isGameInstalled(game(dir.absolutePath))

        assertTrue(installed)
        coVerify(exactly = 0) { gameDao.update(match { it.localPath == null }) }
    }
}
