package com.nendo.argosy.domain.usecase.libretro

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BuiltinPriorityPinTest {

    private lateinit var preferences: UserPreferencesRepository
    private lateinit var platformDao: PlatformDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var detector: EmulatorDetector
    private lateinit var useCase: LibretroMigrationUseCase
    private val pinDone = MutableStateFlow(false)

    private val scummvm = installed("scummvm")
    private val retroarch = installed("retroarch")
    private val azahar = installed("azahar")

    @Before
    fun setUp() {
        preferences = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        detector = mockk(relaxed = true)
        every { preferences.isBuiltinPriorityPinDone() } returns pinDone
        coEvery { preferences.setBuiltinPriorityPinDone() } answers { pinDone.value = true }
        coEvery { detector.detectEmulators() } returns listOf(scummvm)
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        useCase = LibretroMigrationUseCase(preferences, platformDao, emulatorConfigDao, mockk(relaxed = true), detector)
    }

    private fun installed(id: String) = InstalledEmulator(EmulatorRegistry.getById(id)!!, "1.0", 1L)

    private fun platform(id: Long, slug: String, games: Int = 5) = PlatformEntity(
        id = id,
        slug = slug,
        name = slug,
        shortName = slug,
        sortOrder = 0,
        isVisible = true,
        logoPath = null,
        romExtensions = "zip",
        gameCount = games,
        syncEnabled = true,
        customRomPath = null
    )

    private suspend fun pinnedPackageFor(slug: String, vararg emulators: InstalledEmulator): String? {
        coEvery { platformDao.getAllPlatforms() } returns listOf(platform(7L, slug))
        every { detector.getInstalledForPlatform(slug) } returns emulators.toList()
        val inserted = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(inserted)) } returns 1L
        useCase.pinExistingBuiltinPriorityEmulators()
        return if (inserted.isCaptured) inserted.captured.packageName else null
    }

    @Test
    fun `every platform a built-in core plays puts the built-in core first`() {
        val recommended = EmulatorRegistry.getRecommendedEmulators()
        val misses = LibretroCoreRegistry.getSupportedPlatforms()
            .filter { it in recommended }
            .filter { recommended.getValue(it).first() != EmulatorRegistry.BUILTIN_ID }
        assertEquals(emptyList<String>(), misses)
    }

    @Test
    fun `an install with ScummVM games keeps the standalone app it launched before`() = runTest {
        assertEquals(scummvm.def.packageName, pinnedPackageFor("scummvm", scummvm, retroarch))
        coVerify { preferences.setBuiltinPriorityPinDone() }
    }

    @Test
    fun `a RetroArch-only platform keeps RetroArch`() = runTest {
        assertEquals(retroarch.def.packageName, pinnedPackageFor("lynx", retroarch))
    }

    @Test
    fun `3DS keeps a standalone app that outranked the built-in core`() = runTest {
        assertEquals(azahar.def.packageName, pinnedPackageFor("3ds", azahar, retroarch))
    }

    @Test
    fun `3DS with only RetroArch already launched the built-in core and is not pinned`() = runTest {
        assertEquals(null, pinnedPackageFor("3ds", retroarch))
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `a platform with nothing from its list installed already launched the built-in core`() = runTest {
        assertEquals(null, pinnedPackageFor("scummvm"))
        coVerify { preferences.setBuiltinPriorityPinDone() }
    }

    @Test
    fun `a stored platform default is left alone`() = runTest {
        coEvery { emulatorConfigDao.getDefaultForPlatform(7L) } returns EmulatorConfigEntity(
            platformId = 7L, gameId = null, packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            displayName = "Built-in", coreName = null, isDefault = true
        )
        assertEquals(null, pinnedPackageFor("scummvm", scummvm))
    }

    @Test
    fun `platforms outside the changed set are never pinned`() = runTest {
        assertEquals(null, pinnedPackageFor("nes", retroarch))
    }

    @Test
    fun `an emulator scan that returns nothing leaves the pin for the next start`() = runTest {
        coEvery { detector.detectEmulators() } returns emptyList()
        assertEquals(null, pinnedPackageFor("scummvm", scummvm))
        coVerify(exactly = 0) { preferences.setBuiltinPriorityPinDone() }
    }

    @Test
    fun `a second run does nothing`() = runTest {
        pinnedPackageFor("scummvm", scummvm)
        useCase.pinExistingBuiltinPriorityEmulators()
        coVerify(exactly = 1) { emulatorConfigDao.insert(any()) }
    }
}
