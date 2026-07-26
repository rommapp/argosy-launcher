package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage for the single chain that decides which emulator owns a game.
 * [EmulatorResolver.getEmulatorPackageForGame] feeds both launch dispatch and save-path
 * resolution, so a wrong answer sends save discovery and restore into another emulator's
 * directory. [EmulatorResolver.canonicalEmulatorId] is the id the path resolvers key on.
 */
class EmulatorResolverTest {

    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var libretroCoreMgr: LibretroCoreManager
    private lateinit var installedAppResolver: InstalledAppResolver

    private lateinit var resolver: EmulatorResolver

    private val duckstation = EmulatorRegistry.getById("duckstation")!!
    private val retroarch = EmulatorRegistry.getById("retroarch")!!
    private val nethersx2 = EmulatorRegistry.getById("nethersx2")!!
    private val builtin = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!

    @Before
    fun setup() {
        emulatorDetector = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        libretroCoreMgr = mockk(relaxed = true)
        installedAppResolver = mockk(relaxed = true)

        every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())
        coEvery { emulatorConfigDao.getByGameId(any()) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        every { emulatorDetector.getPreferredEmulator(any(), any()) } returns null
        every { installedAppResolver.isAppInstalled(any()) } returns false
        every { libretroCoreMgr.isPlatformSupported(any()) } returns false
        stubDetectorWith()

        resolver = EmulatorResolver(
            emulatorDetector = emulatorDetector,
            emulatorConfigDao = emulatorConfigDao,
            userPreferencesRepository = userPreferencesRepository,
            libretroCoreMgr = libretroCoreMgr,
            installedAppResolver = installedAppResolver
        )
    }

    private fun installedEmulator(def: EmulatorDef): InstalledEmulator {
        return InstalledEmulator(def = def, versionName = "1.0", versionCode = 1L)
    }

    private fun stubDetectorWith(vararg emulators: InstalledEmulator) {
        val list = emulators.toList()
        every { emulatorDetector.installedEmulators } returns mockk {
            every { value } returns list
        }
    }

    private fun createConfig(
        platformId: Long? = null,
        gameId: Long? = null,
        packageName: String? = null,
        displayName: String? = null,
        isDefault: Boolean = false
    ): EmulatorConfigEntity {
        return EmulatorConfigEntity(
            platformId = platformId,
            gameId = gameId,
            packageName = packageName,
            displayName = displayName,
            coreName = null,
            isDefault = isDefault
        )
    }

    @Test
    fun `installed game override beats the platform default so per-game saves land in the per-game emulator`() =
        runTest {
            coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
                gameId = 1L,
                packageName = duckstation.packageName
            )
            coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
                platformId = 10L,
                packageName = retroarch.packageName,
                isDefault = true
            )
            stubDetectorWith(installedEmulator(duckstation), installedEmulator(retroarch))

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "psx")

            assertEquals(duckstation.packageName, result)
        }

    @Test
    fun `installed platform default is used when the game has no override so the platform binding owns the save path`() =
        runTest {
            coEvery { emulatorConfigDao.getByGameId(1L) } returns null
            coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
                platformId = 10L,
                packageName = retroarch.packageName,
                isDefault = true
            )
            stubDetectorWith(installedEmulator(retroarch))

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "gba")

            assertEquals(retroarch.packageName, result)
        }

    @Test
    fun `configured package that is no longer installed is rejected in favour of the preferred emulator`() =
        runTest {
            coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
                gameId = 1L,
                packageName = duckstation.packageName
            )
            stubDetectorWith(installedEmulator(retroarch))
            every { emulatorDetector.getPreferredEmulator("psx", true) } returns
                installedEmulator(retroarch)

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "psx")

            assertEquals(retroarch.packageName, result)
            assertNotEquals(duckstation.packageName, result)
        }

    @Test
    fun `built-in binding is rejected when the built-in libretro toggle is off so saves never target the in-process core`() =
        runTest {
            every { userPreferencesRepository.userPreferences } returns flowOf(
                UserPreferences(builtinLibretroEnabled = false)
            )
            coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
                gameId = 1L,
                packageName = EmulatorRegistry.BUILTIN_PACKAGE
            )
            every { libretroCoreMgr.isPlatformSupported("nes") } returns true
            stubDetectorWith(installedEmulator(builtin), installedEmulator(retroarch))
            every { emulatorDetector.getPreferredEmulator("nes", false) } returns
                installedEmulator(retroarch)

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "nes")

            assertEquals(retroarch.packageName, result)
        }

    @Test
    fun `built-in binding is rejected for a platform no bundled core supports so the external emulator owns the save path`() =
        runTest {
            coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
                platformId = 10L,
                packageName = EmulatorRegistry.BUILTIN_PACKAGE,
                isDefault = true
            )
            every { libretroCoreMgr.isPlatformSupported("ps2") } returns false
            stubDetectorWith(installedEmulator(builtin), installedEmulator(nethersx2))
            every { emulatorDetector.getPreferredEmulator("ps2", true) } returns
                installedEmulator(nethersx2)

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "ps2")

            assertEquals(nethersx2.packageName, result)
        }

    @Test
    fun `ad-hoc package unknown to the registry is returned as-is when the app is installed`() = runTest {
        val adHocPackage = "com.custom.emulator"
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = adHocPackage,
            displayName = "Custom Emu",
            isDefault = true
        )
        stubDetectorWith()
        every { installedAppResolver.isAppInstalled(adHocPackage) } returns true

        val result = resolver.getEmulatorPackageForGame(1L, 10L, "psx")

        assertEquals(adHocPackage, result)
    }

    @Test
    fun `ad-hoc package that is not installed falls through to the preferred emulator`() = runTest {
        val adHocPackage = "com.missing.emulator"
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = adHocPackage,
            isDefault = true
        )
        stubDetectorWith(installedEmulator(duckstation))
        every { installedAppResolver.isAppInstalled(adHocPackage) } returns false
        every { emulatorDetector.getPreferredEmulator("psx", true) } returns
            installedEmulator(duckstation)

        val result = resolver.getEmulatorPackageForGame(1L, 10L, "psx")

        assertEquals(duckstation.packageName, result)
    }

    @Test
    fun `unconfigured game resolves to the preferred emulator for the platform`() = runTest {
        stubDetectorWith(installedEmulator(retroarch))
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns
            installedEmulator(retroarch)

        val result = resolver.getEmulatorPackageForGame(1L, 10L, "nes")

        assertEquals(retroarch.packageName, result)
    }

    @Test
    fun `unconfigured game with no preferred emulator resolves to null rather than an arbitrary package`() =
        runTest {
            stubDetectorWith()
            every { emulatorDetector.getPreferredEmulator("nes", true) } returns null

            val result = resolver.getEmulatorPackageForGame(1L, 10L, "nes")

            assertNull(result)
        }

    @Test
    fun `def backed by a registry package canonicalises to that registry id`() {
        assertEquals("duckstation", resolver.canonicalEmulatorId(duckstation))
    }

    @Test
    fun `family-synthesized fork def collapses to the family base id so RetroArch save paths still match`() {
        val family = EmulatorRegistry.getEmulatorFamilies().first { it.baseId == "retroarch" }
        val forkPackage = "com.retroarch.aarch64.nightly"
        val forkDef = EmulatorRegistry.createDefFromFamily(family, forkPackage)

        val canonicalId = resolver.canonicalEmulatorId(forkDef)

        assertEquals("retroarch", canonicalId)
        assertNotEquals(forkDef.id, canonicalId)
        assertTrue(RetroArchPathResolver.isRetroArch(canonicalId))
    }
}
