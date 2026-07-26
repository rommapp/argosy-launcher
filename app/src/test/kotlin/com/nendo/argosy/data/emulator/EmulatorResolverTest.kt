package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EmulatorResolverTest {

    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var libretroCoreManager: LibretroCoreManager
    private lateinit var installedAppResolver: InstalledAppResolver
    private lateinit var installedEmulators: MutableStateFlow<List<InstalledEmulator>>
    private lateinit var resolver: EmulatorResolver

    @Before
    fun setup() {
        emulatorDetector = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        libretroCoreManager = mockk(relaxed = true)
        installedAppResolver = mockk(relaxed = true)
        installedEmulators = MutableStateFlow(emptyList())

        every { emulatorDetector.installedEmulators } returns installedEmulators
        coEvery { emulatorDetector.detectEmulators() } answers { installedEmulators.value }
        every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { userPreferencesRepository.getBuiltinCoreSelections() } returns flowOf(emptyMap())
        every { libretroCoreManager.isPlatformSupported(any()) } returns true
        every { installedAppResolver.isAppInstalled(any()) } returns false

        resolver = EmulatorResolver(
            emulatorDetector = emulatorDetector,
            emulatorConfigDao = emulatorConfigDao,
            userPreferencesRepository = userPreferencesRepository,
            libretroCoreMgr = libretroCoreManager,
            installedAppResolver = installedAppResolver
        )
    }

    @Test
    fun `configured installed emulator is returned`() = runTest {
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        install(retroArch)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = retroArch.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null

        val resolved = resolver.getEmulatorForGame(1L, 10L, "psx")

        assertEquals(retroArch, resolved)
    }

    @Test
    fun `uninstalled configured emulator falls back to installed preferred emulator`() = runTest {
        val duckStation = EmulatorRegistry.getById("duckstation")!!
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        install(retroArch)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = duckStation.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("psx", true) } returns installed(retroArch)

        val resolved = resolver.getEmulatorForGame(1L, 10L, "psx")

        assertEquals(retroArch, resolved)
    }

    @Test
    fun `platform resolution ignores an uninstalled configured emulator`() = runTest {
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        install(builtIn)
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns config(
            platformId = 10L,
            packageName = retroArch.packageName
        )
        every { emulatorDetector.getPreferredEmulator("psx", true) } returns installed(builtIn)

        val resolved = resolver.getEmulatorForPlatform(10L, "psx")

        assertEquals(builtIn, resolved)
    }

    @Test
    fun `disabled built-in config falls back to external emulator`() = runTest {
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        install(builtIn, retroArch)
        every { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(builtinLibretroEnabled = false)
        )
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = builtIn.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("saturn", false) } returns installed(retroArch)

        val resolved = resolver.getEmulatorForGame(1L, 10L, "saturn")

        assertEquals(retroArch, resolved)
    }

    @Test
    fun `unsupported built-in config falls back to external emulator`() = runTest {
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        val netherSx2 = EmulatorRegistry.getById("nethersx2")!!
        install(builtIn, netherSx2)
        every { libretroCoreManager.isPlatformSupported("ps2") } returns false
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns config(
            platformId = 10L,
            packageName = builtIn.packageName
        )
        every { emulatorDetector.getPreferredEmulator("ps2", false) } returns installed(netherSx2)

        val resolved = resolver.getEmulatorForGame(1L, 10L, "ps2")

        assertEquals(netherSx2, resolved)
    }

    @Test
    fun `configured emulator that does not support the platform is ignored`() = runTest {
        val duckStation = EmulatorRegistry.getById("duckstation")!!
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        install(duckStation, builtIn)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = duckStation.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("snes", true) } returns installed(builtIn)

        val resolved = resolver.getEmulatorForGame(1L, 10L, "snes")

        assertEquals(builtIn, resolved)
    }

    @Test
    fun `stale cache is refreshed for a lower priority configured emulator`() = runTest {
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        install(builtIn)
        every { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(builtinLibretroEnabled = false)
        )
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = builtIn.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns config(
            platformId = 10L,
            packageName = retroArch.packageName
        )
        coEvery { emulatorDetector.detectEmulators() } answers {
            install(builtIn, retroArch)
            installedEmulators.value
        }

        val resolved = resolver.getEmulatorForGame(1L, 10L, "saturn")

        assertEquals(retroArch, resolved)
        coVerify(exactly = 1) { emulatorDetector.detectEmulators() }
    }

    @Test
    fun `core selection uses the mode of fallback emulator`() = runTest {
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        val retroArch = EmulatorRegistry.getById("retroarch")!!
        install(builtIn)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = retroArch.packageName,
            coreName = "yabasanshiro"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("saturn", true) } returns installed(builtIn)

        val emulator = resolver.getEmulatorForGame(1L, 10L, "saturn")!!
        val selection = resolver.resolveCoreSelectionForGame(1L, 10L, "saturn", emulator)

        assertEquals(builtIn, emulator)
        assertEquals("mednafen_saturn", selection?.selectedCore?.id)
    }

    @Test
    fun `invalid game core falls through to valid platform core`() = runTest {
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        install(builtIn)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = builtIn.packageName,
            coreName = "yabasanshiro"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns config(
            platformId = 10L,
            packageName = builtIn.packageName,
            coreName = "mednafen_saturn"
        )

        val selection = resolver.resolveCoreSelectionForGame(1L, 10L, "saturn", builtIn)

        assertEquals("mednafen_saturn", selection?.selectedCore?.id)
    }

    @Test
    fun `standalone emulator has no core selection`() = runTest {
        val yabaSanshiro = EmulatorRegistry.getById("yabasanshiro")!!
        install(yabaSanshiro)
        coEvery { emulatorConfigDao.getByGameId(1L) } returns config(
            gameId = 1L,
            packageName = yabaSanshiro.packageName,
            coreName = "yabasanshiro"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null

        val selection = resolver.resolveCoreSelectionForGame(1L, 10L, "saturn", yabaSanshiro)

        assertNull(selection)
    }

    private fun install(vararg defs: EmulatorDef) {
        installedEmulators.value = defs.map(::installed)
        defs.forEach { def ->
            every { emulatorDetector.getByPackage(def.packageName) } returns def
        }
    }

    private fun installed(def: EmulatorDef): InstalledEmulator =
        InstalledEmulator(def = def, versionName = "1.0", versionCode = 1L)

    private fun config(
        platformId: Long? = null,
        gameId: Long? = null,
        packageName: String,
        coreName: String? = null
    ): EmulatorConfigEntity = EmulatorConfigEntity(
        platformId = platformId,
        gameId = gameId,
        packageName = packageName,
        displayName = packageName,
        coreName = coreName,
        isDefault = gameId == null
    )
}
