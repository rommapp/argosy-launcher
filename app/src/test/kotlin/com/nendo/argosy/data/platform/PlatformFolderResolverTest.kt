package com.nendo.argosy.data.platform

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformFolderResolverTest {

    private val base = File("/storage/roms")
    private val packagesFolder = "/sd/ps3pkg"

    private val discs = platform(1L, "ps3")
    private val packages = platform(2L, "ps3", fsSlug = "ps3pkg", customRomPath = packagesFolder)

    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private fun resolver(romStoragePath: String? = base.absolutePath): PlatformFolderResolver {
        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns flowOf(UserPreferences(romStoragePath = romStoragePath))
        return PlatformFolderResolver(context, platformDao, gameDao, preferences)
    }

    private fun stubPlatforms(vararg platforms: PlatformEntity) {
        coEvery { platformDao.getById(any()) } returns null
        platforms.forEach { p -> coEvery { platformDao.getById(p.id) } returns p }
        coEvery { platformDao.getAllPlatforms() } returns platforms.toList()
    }

    private fun stubGame(id: Long, platformId: Long, slug: String) {
        coEvery { gameDao.getById(id) } returns game(id, platformId, slug)
    }

    @Test
    fun `a shared slug resolves each platform to its own folder`() = runTest {
        stubPlatforms(discs, packages)
        stubGame(10L, packages.id, "ps3")
        stubGame(11L, discs.id, "ps3")
        val resolver = resolver()

        assertEquals(File(packagesFolder), resolver.downloadDirForGame(10L, "ps3"))
        assertEquals(File(base, "ps3"), resolver.downloadDirForGame(11L, "ps3"))
    }

    @Test
    fun `an override on the lower id row leaves the other row on the slug folder`() = runTest {
        val discsOverridden = discs.copy(customRomPath = "/sd/ps3disc")
        val packagesPlain = packages.copy(customRomPath = null)
        stubPlatforms(discsOverridden, packagesPlain)
        stubGame(10L, packagesPlain.id, "ps3")
        stubGame(11L, discsOverridden.id, "ps3")
        val resolver = resolver()

        assertEquals(File(base, "ps3"), resolver.downloadDirForGame(10L, "ps3"))
        assertEquals(File("/sd/ps3disc"), resolver.downloadDirForGame(11L, "ps3"))
    }

    @Test
    fun `a single platform keeps the slug folder under the storage root`() = runTest {
        val snes = platform(3L, "snes")
        stubPlatforms(snes)
        stubGame(20L, snes.id, "snes")

        assertEquals(File(base, "snes"), resolver().downloadDirForGame(20L, "snes"))
        assertEquals(File(base, "snes"), resolver().downloadDirForPlatform(snes.id, "snes"))
    }

    @Test
    fun `without a game folder nothing resolves to app storage`() = runTest {
        every { context.getExternalFilesDir(null) } returns File("/data/app-files")
        val snes = platform(3L, "snes")
        stubPlatforms(snes)
        stubGame(20L, snes.id, "snes")
        val resolver = resolver(romStoragePath = null)

        assertNull(resolver.storageBase())
        assertNull(resolver.downloadDirForPlatform(snes.id, "snes"))
        assertNull(resolver.downloadDirForGame(20L, "snes"))
        assertTrue(resolver.romRootsForPlatform(snes.id, "snes").isEmpty())
    }

    @Test
    fun `without a game folder a platform override is still a launch root`() = runTest {
        stubPlatforms(discs, packages)

        assertEquals(
            listOf(File(packagesFolder)),
            resolver(romStoragePath = null).romRootsForPlatform(packages.id, "ps3")
        )
    }

    @Test
    fun `a blank game folder counts as none`() = runTest {
        assertNull(resolver(romStoragePath = "").storageBase())
    }

    @Test
    fun `a game with no platform row falls back to its slug folder`() = runTest {
        stubPlatforms()
        coEvery { gameDao.getById(30L) } returns null

        assertEquals(File(base, "ps3"), resolver().downloadDirForGame(30L, "ps3"))
    }

    @Test
    fun `the override is a launch root of its own platform only`() = runTest {
        stubPlatforms(discs, packages)
        val resolver = resolver()

        val packageRoots = resolver.romRootsForPlatform(packages.id, "ps3")
        assertTrue(packageRoots.contains(File(packagesFolder)))
        assertTrue(packageRoots.contains(File(base, "ps3")))
        assertTrue(!resolver.romRootsForPlatform(discs.id, "ps3").contains(File(packagesFolder)))
    }

    private fun platform(
        id: Long,
        slug: String,
        fsSlug: String? = null,
        customRomPath: String? = null
    ) = PlatformEntity(
        id = id,
        slug = slug,
        fsSlug = fsSlug,
        name = slug,
        shortName = slug,
        romExtensions = "",
        customRomPath = customRomPath
    )

    private fun game(id: Long, platformId: Long, slug: String) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = slug,
        title = "Game $id",
        sortTitle = "game $id",
        localPath = null,
        fileOrigin = FileOrigin.ROMM_DOWNLOAD,
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
