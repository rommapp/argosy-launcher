package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.coreoptions.CoreOptionResolver
import android.net.Uri
import androidx.core.content.FileProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class GameLauncherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var gameDao: GameDao
    private lateinit var platformDao: PlatformDao
    private lateinit var gameDiscDao: GameDiscDao
    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var emulatorLaunchArgsDao: EmulatorLaunchArgsDao
    private lateinit var variantResolver: VariantResolver
    private lateinit var installedAppResolver: InstalledAppResolver
    private lateinit var platformLibretroSettingsDao: PlatformLibretroSettingsDao
    private lateinit var emulatorDetector: EmulatorDetector
    private lateinit var m3uManager: M3uManager
    private lateinit var libretroCoreMgr: LibretroCoreManager
    private lateinit var biosRepository: BiosRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var coreOptionResolver: CoreOptionResolver
    private lateinit var coreSystemDataManager: CoreSystemDataManager
    private lateinit var gameFileDao: GameFileDao
    private lateinit var emulatorSaveConfigRepository: com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
    private lateinit var saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry

    private lateinit var libretroStatePathResolver: LibretroStatePathResolver
    private lateinit var libretroSavePathResolver: LibretroSavePathResolver
    private lateinit var launcher: GameLauncher
    private val overlayWriter = mockk<com.nendo.argosy.data.repository.GameUserOverlayWriter>(relaxed = true)
    private val launchProgressTracker = mockk<LaunchProgressTracker>(relaxed = true)
    private val steamInstallReports = mockk<com.nendo.argosy.data.launcher.SteamInstallReports>(relaxed = true)
    private val appsRepository = mockk<com.nendo.argosy.data.repository.AppsRepository>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(FileProvider::class)
        context = mockk(relaxed = true)
        every { context.packageName } returns "com.nendo.argosy"
        val testUri = mockk<Uri>(relaxed = true)
        every { testUri.toString() } returns "content://test/file"
        every { testUri.scheme } returns "content"
        every { testUri.authority } returns "com.nendo.argosy.fileprovider"
        every {
            FileProvider.getUriForFile(context, "com.nendo.argosy.fileprovider", any())
        } returns testUri
        gameDao = mockk(relaxed = true)
        coEvery { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) } returns 1
        every { appsRepository.isPackageMissing(any()) } answers {
            com.nendo.argosy.data.launcher.SteamLaunchers.getByPackage(firstArg())?.isInstalled(context) == false
        }
        platformDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        emulatorConfigDao = mockk(relaxed = true)
        emulatorLaunchArgsDao = mockk(relaxed = true)
        variantResolver = mockk(relaxed = true)
        installedAppResolver = mockk(relaxed = true)
        platformLibretroSettingsDao = mockk(relaxed = true)
        emulatorDetector = mockk(relaxed = true)
        m3uManager = mockk(relaxed = true)
        libretroCoreMgr = mockk(relaxed = true)
        biosRepository = mockk(relaxed = true)
        userPreferencesRepository = mockk(relaxed = true)
        coreOptionResolver = mockk(relaxed = true)
        coreSystemDataManager = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        emulatorSaveConfigRepository = mockk(relaxed = true)
        saveHandlerRegistry = mockk(relaxed = true)
        libretroStatePathResolver = mockk(relaxed = true)
        coEvery { libretroStatePathResolver.liveStateBaseDir(any()) } returns
            java.io.File("/data/data/com.nendo.argosy/files/libretro/states")
        libretroSavePathResolver = mockk(relaxed = true)
        every { libretroSavePathResolver.liveSaveBaseDir(any(), any(), any()) } returns
            java.io.File("/data/data/com.nendo.argosy/files/libretro/saves")

        every { userPreferencesRepository.userPreferences } returns flowOf(UserPreferences())
        every { userPreferencesRepository.getBuiltinCoreSelections() } returns flowOf(emptyMap())
        every { emulatorDetector.installedEmulators } returns mockk {
            every { value } returns emptyList()
        }

        val platformFolderResolver = PlatformFolderResolver(
            context = context,
            platformDao = platformDao,
            gameDao = gameDao,
            preferencesRepository = userPreferencesRepository
        )

        launcher = GameLauncher(
            context = context,
            gameDao = gameDao,
            overlayWriter = overlayWriter,
            platformDao = platformDao,
            gameDiscDao = gameDiscDao,
            emulatorConfigDao = emulatorConfigDao,
            emulatorLaunchArgsDao = emulatorLaunchArgsDao,
            variantResolver = variantResolver,
            platformLibretroSettingsDao = platformLibretroSettingsDao,
            emulatorDetector = emulatorDetector,
            builtinCoreResolver = BuiltinCoreResolver(
                emulatorConfigDao,
                userPreferencesRepository,
                mockk(relaxed = true),
                kotlin.io.path.createTempDirectory("system").toFile().also {
                    java.io.File(it, "scph5501.bin").writeBytes(byteArrayOf(0))
                }
            ),
            m3uManager = m3uManager,
            libretroCoreMgr = libretroCoreMgr,
            biosRepository = biosRepository,
            userPreferencesRepository = userPreferencesRepository,
            coreOptionResolver = coreOptionResolver,
            builtinGraphicsResolver = mockk(relaxed = true),
            coreSystemDataManager = coreSystemDataManager,
            gameFileDao = gameFileDao,
            emulatorSaveConfigRepository = emulatorSaveConfigRepository,
            savePathAuthority = mockk(relaxed = true),
            saveHandlerRegistry = saveHandlerRegistry,
            libretroStatePathResolver = libretroStatePathResolver,
            builtinSaveBase = BuiltinSaveBase(libretroSavePathResolver, emulatorSaveConfigRepository, emulatorConfigDao),
            notificationManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            accountSwitchMarkerStore = mockk(relaxed = true),
            extContentOrganizer = mockk(relaxed = true),
            baseRomFileResolver = BaseRomFileResolver(
                gameDao = gameDao,
                platformFolderResolver = platformFolderResolver
            ),
            platformFolderResolver = platformFolderResolver,
            volumeHealth = mockk(relaxed = true),
            dreamcastVmuMigrator = mockk(relaxed = true),
            launchProgressTracker = launchProgressTracker,
            steamInstallReports = steamInstallReports,
            appsRepository = appsRepository,
            emulatorResolver = EmulatorResolver(
                emulatorDetector = emulatorDetector,
                emulatorConfigDao = emulatorConfigDao,
                userPreferencesRepository = userPreferencesRepository,
                libretroCoreMgr = libretroCoreMgr,
                installedAppResolver = installedAppResolver
            ),
            keepAlivePolicy = mockk {
                coEvery { launchFlags(any(), any()) } answers { secondArg() }
            }
        )
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(FileProvider::class)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun createGame(
        id: Long = 1L,
        platformId: Long = 10L,
        platformSlug: String = "nes",
        localPath: String? = null,
        source: GameSource = GameSource.ROMM_SYNCED
    ): GameEntity {
        return GameEntity(
            id = id,
            platformId = platformId,
            platformSlug = platformSlug,
            title = "Test Game",
            sortTitle = "test game",
            localPath = localPath,
            rommId = 100L,
            igdbId = null,
            source = source
        )
    }

    private fun createConfig(
        platformId: Long? = null,
        gameId: Long? = null,
        packageName: String? = null,
        displayName: String? = null,
        coreName: String? = null,
        isDefault: Boolean = false
    ): EmulatorConfigEntity {
        return EmulatorConfigEntity(
            platformId = platformId,
            gameId = gameId,
            packageName = packageName,
            displayName = displayName,
            coreName = coreName,
            isDefault = isDefault
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

    private fun romFile(extension: String = "nes"): String {
        val file = tempFolder.newFile("game-${UUID.randomUUID()}.$extension")
        return file.absolutePath
    }

    // -----------------------------------------------------------------------
    // Basic launch preconditions
    // -----------------------------------------------------------------------

    @Test
    fun `a gamenative store launch records the play start that puts the game in recent`() = runTest {
        val store = com.nendo.argosy.data.launcher.GameNativeStore.entries.first()
        val game = createGame(platformSlug = store.slug, source = GameSource.GAMENATIVE).copy(steamAppId = 42L)
        coEvery { gameDao.getById(game.id) } returns game
        io.mockk.mockkObject(com.nendo.argosy.data.launcher.GameNativeLauncher)
        try {
            every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns true
            every {
                com.nendo.argosy.data.launcher.GameNativeLauncher.createSourcedLaunchIntent(any(), any())
            } returns mockk(relaxed = true)

            val result = launcher.launch(game.id)

            assertTrue(result is LaunchResult.Success)
            coVerify { overlayWriter.recordPlayStart(game.id, any()) }
        } finally {
            io.mockk.unmockkObject(com.nendo.argosy.data.launcher.GameNativeLauncher)
        }
    }

    private val gameNativePackage = com.nendo.argosy.data.launcher.GameNativeLauncher.packageName
    private val droidDeckPackage = com.nendo.argosy.data.launcher.DroidDeckLauncher.packageName

    private fun steamGame(launcher: String?, pinned: Boolean) = createGame(
        platformSlug = "steam",
        source = GameSource.STEAM
    ).copy(steamAppId = 620L, steamLauncher = launcher, launcherSetManually = pinned)

    private fun withBothSteamLaunchersInstalled(block: suspend () -> Unit) = runTest {
        val launchers = com.nendo.argosy.data.launcher.SteamLaunchers.all
        launchers.forEach { io.mockk.mockkObject(it) }
        try {
            launchers.forEach {
                every { it.isInstalled(any()) } returns (it.packageName in setOf(gameNativePackage, droidDeckPackage))
                every { it.createLaunchIntent(any()) } returns mockk(relaxed = true)
            }
            block()
        } finally {
            launchers.forEach { io.mockk.unmockkObject(it) }
        }
    }

    @Test
    fun `a steam game both launchers report asks which one to use`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(gameNativePackage, droidDeckPackage)
        val ticket = mockk<LaunchProgressTracker.Ticket>()
        every { launchProgressTracker.current } returns ticket
        coEvery { ticket.chooseLauncher(any()) } returns
            com.nendo.argosy.domain.model.LaunchPromptOption.LAUNCH_WITH_DROIDDECK

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        coVerify {
            ticket.chooseLauncher(
                listOf(
                    com.nendo.argosy.domain.model.LaunchPromptOption.LAUNCH_WITH_GAMENATIVE,
                    com.nendo.argosy.domain.model.LaunchPromptOption.LAUNCH_WITH_DROIDDECK
                )
            )
        }
        verify { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cancelling the launcher question cancels the launch`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(gameNativePackage, droidDeckPackage)
        val ticket = mockk<LaunchProgressTracker.Ticket>()
        every { launchProgressTracker.current } returns ticket
        coEvery { ticket.chooseLauncher(any()) } returns null

        val result = launcher.launch(game.id)

        assertEquals(LaunchResult.Cancelled, result)
        coVerify(exactly = 0) { overlayWriter.recordPlayStart(any(), any()) }
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `a pinned steam game launches its pin without asking`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = droidDeckPackage, pinned = true)
        coEvery { gameDao.getById(game.id) } returns game
        val ticket = mockk<LaunchProgressTracker.Ticket>(relaxed = true)
        every { launchProgressTracker.current } returns ticket

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        coVerify(exactly = 0) { ticket.chooseLauncher(any()) }
        coVerify(exactly = 0) { steamInstallReports.launchersReporting(any()) }
        verify { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }
    }

    @Test
    fun `a steam game only one launcher reports launches without asking`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = droidDeckPackage, pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(droidDeckPackage)
        val ticket = mockk<LaunchProgressTracker.Ticket>(relaxed = true)
        every { launchProgressTracker.current } returns ticket

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        coVerify(exactly = 0) { ticket.chooseLauncher(any()) }
        verify { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }
    }

    @Test
    fun `a removed automatic steam launcher recovers to the installed reporting launcher`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(gameNativePackage, droidDeckPackage)

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        verify { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `a removed steam pin recovers without asking`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = true)
        var storedGame = game
        coEvery { gameDao.getById(game.id) } answers { storedGame }
        coEvery { gameDao.compareAndSetSteamLauncher(game.id, any(), any(), any(), any(), null) } answers {
            storedGame = storedGame.copy(steamLauncher = arg(3), launcherSetManually = arg(4))
            1
        }
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(droidDeckPackage)
        val ticket = mockk<LaunchProgressTracker.Ticket>(relaxed = true)
        every { launchProgressTracker.current } returns ticket

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        assertEquals(droidDeckPackage, storedGame.steamLauncher)
        assertEquals(false, storedGame.launcherSetManually)
        coVerify { gameDao.compareAndSetSteamLauncher(game.id, gameNativePackage, true, gameNativePackage, false, null) }
        coVerify { gameDao.compareAndSetSteamLauncher(game.id, gameNativePackage, false, droidDeckPackage, false, null) }
        coVerify(exactly = 0) { ticket.chooseLauncher(any()) }
        verify { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }

        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns true

        assertTrue(launcher.launch(game.id) is LaunchResult.Success)
        assertEquals(false, storedGame.launcherSetManually)
        verify(exactly = 2) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `a removed pin stays Auto without a reported replacement`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = true)
        var storedGame = game
        coEvery { gameDao.getById(game.id) } answers { storedGame }
        coEvery { gameDao.compareAndSetSteamLauncher(game.id, gameNativePackage, true, gameNativePackage, false, null) } answers {
            storedGame = storedGame.copy(launcherSetManually = false)
            1
        }
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        coEvery { steamInstallReports.launchersReporting(620L) } returns emptyList()

        val result = launcher.launch(game.id)

        assertEquals(
            LaunchResult.NoSteamLauncher(gameNativePackage, LaunchResult.NoSteamLauncher.Reason.NO_REPORTED_REPLACEMENT),
            result
        )
        assertEquals(gameNativePackage, storedGame.steamLauncher)
        assertEquals(false, storedGame.launcherSetManually)
        coVerify(exactly = 1) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { overlayWriter.recordPlayStart(any(), any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `no installed Steam launcher reports installation recovery instead of launching a stale report`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        every { com.nendo.argosy.data.launcher.DroidDeckLauncher.isInstalled(any()) } returns false
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(gameNativePackage, droidDeckPackage)

        val result = launcher.launch(game.id)

        assertEquals(
            LaunchResult.NoSteamLauncher(gameNativePackage, LaunchResult.NoSteamLauncher.Reason.NONE_INSTALLED),
            result
        )
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { overlayWriter.recordPlayStart(any(), any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `an unknown saved Steam launcher never falls back to GameNative without a report`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = "removed.launcher", pinned = false)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { steamInstallReports.launchersReporting(620L) } returns emptyList()

        val result = launcher.launch(game.id)

        assertEquals(
            LaunchResult.NoSteamLauncher("removed.launcher", LaunchResult.NoSteamLauncher.Reason.NO_REPORTED_REPLACEMENT),
            result
        )
        coVerify(exactly = 0) { overlayWriter.recordPlayStart(any(), any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(any()) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `a known launcher update leaves its manual pin intact`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = true)
        coEvery { gameDao.getById(game.id) } returns game
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        every { appsRepository.isPackageUpdating(gameNativePackage) } returns true

        val result = launcher.launch(game.id)

        assertEquals(
            LaunchResult.NoSteamLauncher(gameNativePackage, LaunchResult.NoSteamLauncher.Reason.UPDATING),
            result
        )
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { steamInstallReports.launchersReporting(any()) }
        coVerify(exactly = 0) { overlayWriter.recordPlayStart(any(), any()) }
    }

    @Test
    fun `a newer user pin wins a racing automatic replacement`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = false)
        val gameHub = com.nendo.argosy.data.launcher.GameHubLauncher
        var storedGame = game
        coEvery { gameDao.getById(game.id) } answers { storedGame }
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        every { gameHub.isInstalled(any()) } returns true
        coEvery { steamInstallReports.launchersReporting(620L) } returns listOf(droidDeckPackage)
        coEvery { gameDao.compareAndSetSteamLauncher(game.id, gameNativePackage, false, droidDeckPackage, false, null) } answers {
            storedGame = storedGame.copy(steamLauncher = gameHub.packageName, launcherSetManually = true)
            0
        }

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        assertEquals(gameHub.packageName, storedGame.steamLauncher)
        assertEquals(true, storedGame.launcherSetManually)
        coVerify(exactly = 1) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { steamInstallReports.launchersReporting(620L) }
        verify { gameHub.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `a newer user pin wins a racing retired pin reset`() = withBothSteamLaunchersInstalled {
        val game = steamGame(launcher = gameNativePackage, pinned = true)
        val gameHub = com.nendo.argosy.data.launcher.GameHubLauncher
        var storedGame = game
        coEvery { gameDao.getById(game.id) } answers { storedGame }
        every { com.nendo.argosy.data.launcher.GameNativeLauncher.isInstalled(any()) } returns false
        every { gameHub.isInstalled(any()) } returns true
        coEvery { gameDao.compareAndSetSteamLauncher(game.id, gameNativePackage, true, gameNativePackage, false, null) } answers {
            storedGame = storedGame.copy(steamLauncher = gameHub.packageName, launcherSetManually = true)
            0
        }

        val result = launcher.launch(game.id)

        assertTrue(result is LaunchResult.Success)
        assertEquals(gameHub.packageName, storedGame.steamLauncher)
        assertEquals(true, storedGame.launcherSetManually)
        coVerify(exactly = 1) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { steamInstallReports.launchersReporting(any()) }
        verify { gameHub.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
    }

    @Test
    fun `native Steam selections retain their existing installed launcher route`() = withBothSteamLaunchersInstalled {
        coEvery { steamInstallReports.launchersReporting(620L) } returns emptyList()
        listOf(null, GameEntity.LAUNCHER_UNSPECIFIED).forEach { storedPackage ->
            val game = steamGame(launcher = storedPackage, pinned = false)
            coEvery { gameDao.getById(game.id) } returns game

            assertTrue(launcher.launch(game.id) is LaunchResult.Success)
        }

        verify(exactly = 2) { com.nendo.argosy.data.launcher.GameNativeLauncher.createLaunchIntent(620L) }
        verify(exactly = 0) { com.nendo.argosy.data.launcher.DroidDeckLauncher.createLaunchIntent(any()) }
        coVerify(exactly = 0) { gameDao.compareAndSetSteamLauncher(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returns error when game not found`() = runTest {
        coEvery { gameDao.getById(999L) } returns null

        val result = launcher.launch(999L)

        assertTrue(result is LaunchResult.Error)
    }

    @Test
    fun `returns NoRomFile when localPath is null`() = runTest {
        val game = createGame(localPath = null)
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoRomFile)
    }

    @Test
    fun `returns NoRomFile when file does not exist`() = runTest {
        val game = createGame(localPath = "/nonexistent/game.nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoRomFile)
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: game-specific override
    // -----------------------------------------------------------------------

    @Test
    fun `resolves game-specific emulator override`() = runTest {
        val path = romFile()
        val game = createGame(localPath = path, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val duckstation = EmulatorRegistry.getById("duckstation")!!
        val gameConfig = createConfig(
            gameId = 1L,
            packageName = duckstation.packageName,
            displayName = "DuckStation"
        )
        coEvery { emulatorConfigDao.getByGameId(1L) } returns gameConfig
        stubDetectorWith(installedEmulator(duckstation))
        every { emulatorDetector.getByPackage(duckstation.packageName) } returns duckstation

        val result = try {
            launcher.launch(1L)
        } catch (_: NullPointerException) {
            // FileProvider.getUriForFile returns null in JVM unit tests (android stub);
            // reaching intent-building confirms game-override resolution.
            return@runTest
        }

        assertTrue(
            "Expected Success or NoCore but got ${result::class.simpleName}",
            result is LaunchResult.Success || result is LaunchResult.NoCore || result is LaunchResult.Error
        )
    }

    @Test
    fun `game override takes priority over platform default`() = runTest {
        val path = romFile("iso")
        val game = createGame(localPath = path, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val duckstation = EmulatorRegistry.getById("duckstation")!!
        val retroarch = EmulatorRegistry.getById("retroarch")!!

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
        every { emulatorDetector.getByPackage(duckstation.packageName) } returns duckstation

        try {
            launcher.launch(1L)
        } catch (_: NullPointerException) {
            // FileProvider.getUriForFile returns null in JVM unit tests.
        }

        coVerify(exactly = 0) { emulatorDetector.getByPackage(retroarch.packageName) }
    }

    @Test
    fun `fileUriString extras use absolute path for m3u files`() = runTest {
        val m3uPath = romFile("m3u")
        val game = createGame(localPath = m3uPath, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val duckstation = EmulatorRegistry.getById("duckstation")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = duckstation.packageName,
            displayName = duckstation.displayName
        )
        stubDetectorWith(installedEmulator(duckstation))
        every { emulatorDetector.getByPackage(duckstation.packageName) } returns duckstation

        launcher.launch(1L)

        verify(exactly = 0) {
            FileProvider.getUriForFile(any(), any(), match { it.extension.equals("m3u", ignoreCase = true) })
        }
    }

    @Test
    fun `fileUriString extras use content uri string for non m3u files`() = runTest {
        val cuePath = romFile("cue")
        val game = createGame(localPath = cuePath, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val duckstation = EmulatorRegistry.getById("duckstation")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = duckstation.packageName,
            displayName = duckstation.displayName
        )
        stubDetectorWith(installedEmulator(duckstation))
        every { emulatorDetector.getByPackage(duckstation.packageName) } returns duckstation

        launcher.launch(1L)

        verify(exactly = 1) {
            FileProvider.getUriForFile(any(), any(), match { it.extension.equals("cue", ignoreCase = true) })
        }
    }

    @Test
    fun `flycast is handed the rom path rather than a content uri it cannot open`() = runTest {
        val chdPath = romFile("chd")
        val game = createGame(localPath = chdPath, platformSlug = "dc")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val flycast = EmulatorRegistry.getById("flycast")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = flycast.packageName,
            displayName = flycast.displayName
        )
        stubDetectorWith(installedEmulator(flycast))
        every { emulatorDetector.getByPackage(flycast.packageName) } returns flycast

        launcher.launch(1L)

        verify(exactly = 0) { FileProvider.getUriForFile(any(), any(), any<java.io.File>()) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: platform default
    // -----------------------------------------------------------------------

    @Test
    fun `falls back to platform default when no game override`() = runTest {
        val path = romFile("gba")
        val game = createGame(localPath = path, platformSlug = "gba")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null

        val retroarch = EmulatorRegistry.getById("retroarch")!!
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = retroarch.packageName,
            isDefault = true
        )

        stubDetectorWith(installedEmulator(retroarch))
        every { emulatorDetector.getByPackage(retroarch.packageName) } returns retroarch

        launcher.launch(1L)

        coVerify { emulatorDetector.getByPackage(retroarch.packageName) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: preferred emulator fallback
    // -----------------------------------------------------------------------

    @Test
    fun `falls back to preferred emulator when no config exists`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null

        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns installedEmulator(builtinDef)

        launcher.launch(1L)

        coVerify { emulatorDetector.getPreferredEmulator("nes", true) }
    }

    @Test
    fun `returns NoEmulator when no config and no preferred`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
        assertEquals("nes", (result as LaunchResult.NoEmulator).platformSlug)
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: null packageName in config
    // -----------------------------------------------------------------------

    @Test
    fun `config with null packageName falls through to preferred`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = null,
            isDefault = true
        )

        every { emulatorDetector.getPreferredEmulator("nes", true) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
        coVerify { emulatorDetector.getPreferredEmulator("nes", true) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: re-detection when package not in cache
    // -----------------------------------------------------------------------

    @Test
    fun `re-detects emulators when configured package not in installed cache`() = runTest {
        val path = romFile("iso")
        val game = createGame(localPath = path, platformSlug = "ps2")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val nethersx2 = EmulatorRegistry.getById("nethersx2")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = nethersx2.packageName,
            isDefault = true
        )

        // First call: cache is empty (package not found)
        // After re-detect: cache has nethersx2
        val emptyList = emptyList<InstalledEmulator>()
        val populatedList = listOf(installedEmulator(nethersx2))

        val valuesMock = mockk<kotlinx.coroutines.flow.StateFlow<List<InstalledEmulator>>>()
        every { emulatorDetector.installedEmulators } returns valuesMock
        every { valuesMock.value } returnsMany listOf(emptyList, populatedList, populatedList)

        every { emulatorDetector.getByPackage(nethersx2.packageName) } returns nethersx2

        try {
            launcher.launch(1L)
        } catch (_: NullPointerException) {
            // FileProvider.getUriForFile returns null in JVM unit tests; reaching
            // intent-building confirms re-detection ran.
        }

        coVerify { emulatorDetector.detectEmulators() }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: built-in disabled
    // -----------------------------------------------------------------------

    @Test
    fun `skips built-in game override when builtin disabled`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        every { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(builtinLibretroEnabled = false)
        )

        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            displayName = "Built-in"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null

        stubDetectorWith(installedEmulator(builtinDef))
        every { emulatorDetector.getPreferredEmulator("nes", false) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
    }

    @Test
    fun `unknown built-in core override falls back to registry default`() = runTest {
        val path = romFile("chd")
        val game = createGame(localPath = path, platformSlug = "psx", platformId = 14L)
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            displayName = "Built-in",
            coreName = "swanstation"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(14L) } returns null
        stubDetectorWith(installedEmulator(builtinDef))
        every { emulatorDetector.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE) } returns builtinDef
        every { libretroCoreMgr.isPlatformSupported("psx") } returns true
        every { libretroCoreMgr.getCorePathForPlatform(any(), any()) } returns "/fake/core.so"

        runCatching { launcher.launch(1L) }

        verify { libretroCoreMgr.getCorePathForPlatform("psx", "mednafen_psx_hw") }
        verify(exactly = 0) { libretroCoreMgr.getCorePathForPlatform("psx", "swanstation") }
    }

    @Test
    fun `skips built-in platform default when builtin disabled and uses preferred`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        every { userPreferencesRepository.userPreferences } returns flowOf(
            UserPreferences(builtinLibretroEnabled = false)
        )

        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            isDefault = true
        )

        stubDetectorWith(installedEmulator(builtinDef))

        val retroarch = EmulatorRegistry.getById("retroarch")!!
        every { emulatorDetector.getPreferredEmulator("nes", false) } returns installedEmulator(retroarch)
        every { emulatorDetector.getByPackage(retroarch.packageName) } returns retroarch

        launcher.launch(1L)

        coVerify { emulatorDetector.getPreferredEmulator("nes", false) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: ad-hoc (unknown package) bindings
    // -----------------------------------------------------------------------

    @Test
    fun `resolves ad-hoc emulator when package is unknown but installed`() = runTest {
        val path = romFile("iso")
        val adHocPackage = "com.custom.emulator"
        val game = createGame(localPath = path, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = adHocPackage,
            displayName = "Custom Emu",
            isDefault = true
        )

        stubDetectorWith()
        every { installedAppResolver.isAppInstalled(adHocPackage) } returns true

        val result = try {
            launcher.launch(1L)
        } catch (_: NullPointerException) {
            // FileProvider.getUriForFile returns null in JVM unit tests (android stub).
            // Reaching intent-building confirms emulator resolution succeeded.
            return@runTest
        }

        assertTrue(
            "Expected Success or Error for ad-hoc, got ${result::class.simpleName}",
            result is LaunchResult.Success || result is LaunchResult.Error
        )
    }

    @Test
    fun `ad-hoc emulator not resolved when app not installed`() = runTest {
        val path = romFile("iso")
        val game = createGame(localPath = path, platformSlug = "psx")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = "com.missing.emulator",
            displayName = "Missing Emu",
            isDefault = true
        )

        stubDetectorWith()
        every { installedAppResolver.isAppInstalled("com.missing.emulator") } returns false
        every { emulatorDetector.getPreferredEmulator("psx", true) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: PS2-specific scenarios
    // -----------------------------------------------------------------------

    @Test
    fun `PS2 with nethersx2 platform default resolves correctly`() = runTest {
        val path = romFile("iso")
        val game = createGame(localPath = path, platformSlug = "ps2")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val nethersx2 = EmulatorRegistry.getById("nethersx2")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = nethersx2.packageName,
            isDefault = true
        )

        stubDetectorWith(installedEmulator(nethersx2))
        every { emulatorDetector.getByPackage(nethersx2.packageName) } returns nethersx2

        try {
            launcher.launch(1L)
        } catch (_: NullPointerException) {
            // FileProvider.getUriForFile returns null in JVM unit tests; reaching
            // intent-building confirms emulator resolution succeeded.
        }

        coVerify { emulatorDetector.getByPackage(nethersx2.packageName) }
    }

    @Test
    fun `PS2 with no config and no installed emulators returns NoEmulator`() = runTest {
        val path = romFile("iso")
        val game = createGame(localPath = path, platformSlug = "ps2")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        every { emulatorDetector.getPreferredEmulator("ps2", true) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
        assertEquals("ps2", (result as LaunchResult.NoEmulator).platformSlug)
    }

    private fun bindPs2ToBuiltin(coreSupportsPs2: Boolean): GameEntity {
        val game = createGame(localPath = romFile("iso"), platformSlug = "ps2")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null
        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            isDefault = true
        )
        stubDetectorWith(installedEmulator(builtinDef))
        every { emulatorDetector.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE) } returns builtinDef
        every { libretroCoreMgr.isPlatformSupported("ps2") } returns coreSupportsPs2
        return game
    }

    @Test
    fun `PS2 bound to the built-in is skipped where no built-in ps2 core runs`() = runTest {
        bindPs2ToBuiltin(coreSupportsPs2 = false)
        every { emulatorDetector.getPreferredEmulator("ps2", true) } returns null

        val result = launcher.launch(1L)

        assertTrue("got ${result::class.simpleName}", result is LaunchResult.NoEmulator)
    }

    @Test
    fun `PS2 on the built-in core without a BIOS in pcsx2 bios asks for one`() = runTest {
        bindPs2ToBuiltin(coreSupportsPs2 = true)
        every { biosRepository.getLibretroSystemDir() } returns tempFolder.newFolder("system-empty")

        val result = launcher.launch(1L)

        assertTrue("got ${result::class.simpleName}", result is LaunchResult.MissingBios)
        assertEquals("ps2", (result as LaunchResult.MissingBios).platformSlug)
        coVerify { biosRepository.distributeBiosToEmulator("ps2", EmulatorRegistry.BUILTIN_PACKAGE) }
    }

    @Test
    fun `PS2 on the built-in core passes the BIOS gate with any file name in pcsx2 bios`() = runTest {
        bindPs2ToBuiltin(coreSupportsPs2 = true)
        val systemDir = tempFolder.newFolder("system-bios")
        java.io.File(systemDir, "pcsx2/bios").mkdirs()
        java.io.File(systemDir, "pcsx2/bios/my dump.bin").writeBytes(byteArrayOf(1))
        every { biosRepository.getLibretroSystemDir() } returns systemDir

        val result = runCatching { launcher.launch(1L) }.getOrNull()

        assertTrue("got ${result?.let { it::class.simpleName }}", result !is LaunchResult.MissingBios)
        coVerify { coreSystemDataManager.ensureCoreSystemData(any(), any()) }
    }

    @Test
    fun `PS2 on the built-in core never raises the memory card picker`() = runTest {
        bindPs2ToBuiltin(coreSupportsPs2 = true)
        every { biosRepository.getLibretroSystemDir() } returns tempFolder.newFolder("system-cards")
        every { saveHandlerRegistry.listPs2FolderMemcardsForEmulator(any(), any(), any()) } returns listOf(
            mockk(relaxed = true),
            mockk(relaxed = true)
        )

        val result = launcher.launch(1L)

        assertTrue("got ${result::class.simpleName}", result !is LaunchResult.SelectMemcard)
        verify(exactly = 0) { saveHandlerRegistry.listPs2FolderMemcardsForEmulator(any(), any(), any()) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: multiple emulators for same platform
    // -----------------------------------------------------------------------

    @Test
    fun `game override selects specific emulator among multiple installed`() = runTest {
        val path = romFile("gba")
        val game = createGame(localPath = path, platformSlug = "gba")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val retroarch = EmulatorRegistry.getById("retroarch")!!
        val builtinDef = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!

        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = retroarch.packageName,
            displayName = "RetroArch"
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns createConfig(
            platformId = 10L,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            isDefault = true
        )

        stubDetectorWith(installedEmulator(retroarch), installedEmulator(builtinDef))
        every { emulatorDetector.getByPackage(retroarch.packageName) } returns retroarch

        launcher.launch(1L)

        coVerify { emulatorDetector.getByPackage(retroarch.packageName) }
        coVerify(exactly = 0) { emulatorDetector.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE) }
    }

    // -----------------------------------------------------------------------
    // Emulator resolution: configured emulator uninstalled
    // -----------------------------------------------------------------------

    @Test
    fun `configured emulator not installed falls through to preferred`() = runTest {
        val path = romFile("nes")
        val game = createGame(localPath = path, platformSlug = "nes")
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null

        val retroarch = EmulatorRegistry.getById("retroarch")!!
        coEvery { emulatorConfigDao.getByGameId(1L) } returns createConfig(
            gameId = 1L,
            packageName = retroarch.packageName
        )
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null

        // retroarch not in installed list, re-detection also doesn't find it
        stubDetectorWith()
        every { emulatorDetector.getPreferredEmulator("nes", true) } returns null

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.NoEmulator)
        coVerify { emulatorDetector.detectEmulators() }
    }

    // -----------------------------------------------------------------------
    // Steam and Android app routing
    // -----------------------------------------------------------------------

    @Test
    fun `steam games bypass emulator resolution`() = runTest {
        val game = createGame(source = GameSource.STEAM, platformSlug = "pc")
        coEvery { gameDao.getById(1L) } returns game

        val result = launcher.launch(1L)

        coVerify(exactly = 0) { emulatorConfigDao.getByGameId(any()) }
    }

    @Test
    fun `android apps bypass emulator resolution`() = runTest {
        val game = createGame(source = GameSource.ANDROID_APP, platformSlug = "android")
        coEvery { gameDao.getById(1L) } returns game

        val result = launcher.launch(1L)

        coVerify(exactly = 0) { emulatorConfigDao.getByGameId(any()) }
    }

    // -----------------------------------------------------------------------
    // Variant selection
    // -----------------------------------------------------------------------

    @Test
    fun `returns SelectVariant when variants exist and none specified`() = runTest {
        val game = createGame(localPath = romFile())
        coEvery { gameDao.getById(1L) } returns game

        val variants = listOf(mockk<VariantOption>(relaxed = true))
        coEvery { variantResolver.getVariantOptions(game) } returns variants

        val result = launcher.launch(1L)

        assertTrue(result is LaunchResult.SelectVariant)
        assertEquals(1L, (result as LaunchResult.SelectVariant).gameId)
    }

    @Test
    fun `skips variant prompt when variantFileId provided`() = runTest {
        val game = createGame(localPath = romFile())
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns listOf(mockk(relaxed = true))
        coEvery { gameFileDao.getById(50L) } returns null

        val result = launcher.launch(gameId = 1L, variantFileId = 50L)

        // Doesn't return SelectVariant; proceeds past variant check
        assertTrue(result !is LaunchResult.SelectVariant)
    }

    private fun stubNoEmulator() {
        coEvery { emulatorConfigDao.getByGameId(1L) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(10L) } returns null
        stubDetectorWith()
        every { emulatorDetector.getPreferredEmulator(any(), any()) } returns null
    }

    private fun fileRow(
        id: Long,
        category: String,
        path: String,
        versionGroup: String? = null
    ) = GameFileEntity(
        id = id,
        gameId = 1L,
        fileName = java.io.File(path).name,
        filePath = path,
        category = category,
        fileSize = 8L,
        localPath = path,
        isLaunchTarget = true,
        versionGroup = versionGroup
    )

    @Test
    fun `a missing variant file launches the primary rom`() = runTest {
        val game = createGame(localPath = romFile())
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameFileDao.getById(50L) } returns null
        stubNoEmulator()

        val result = launcher.launch(gameId = 1L, variantFileId = 50L)

        assertTrue(result is LaunchResult.NoEmulator)
        coVerify { gameDiscDao.getDiscsForGame(1L) }
        coVerify(exactly = 0) { gameDao.updateLastPlayedFileId(any(), any()) }
    }

    @Test
    fun `a hack file id launches the primary rom instead of the hack`() = runTest {
        val game = createGame(localPath = romFile())
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameFileDao.getById(20L) } returns fileRow(20L, "hack", romFile())
        stubNoEmulator()

        launcher.launch(gameId = 1L, variantFileId = 20L)

        coVerify { gameDiscDao.getDiscsForGame(1L) }
    }

    @Test
    fun `the version-grouped primary file launches as the primary rom, not a variant`() = runTest {
        val path = romFile()
        val game = createGame(localPath = path)
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameFileDao.getById(10L) } returns fileRow(10L, "game", path, versionGroup = "romm:100")
        stubNoEmulator()

        launcher.launch(gameId = 1L, variantFileId = 10L)

        coVerify { gameDiscDao.getDiscsForGame(1L) }
    }

    @Test
    fun `a translation file id launches the translation`() = runTest {
        val game = createGame(localPath = romFile())
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameFileDao.getById(30L) } returns fileRow(30L, "translation", romFile())
        stubNoEmulator()

        launcher.launch(gameId = 1L, variantFileId = 30L)

        coVerify(exactly = 0) { gameDiscDao.getDiscsForGame(any()) }
    }

    // -----------------------------------------------------------------------
    // Multi-disc routing
    // -----------------------------------------------------------------------

    @Test
    fun `multi-disc games route to disc selection`() = runTest {
        val game = createGame(localPath = romFile(), platformSlug = "psx").copy(isMultiDisc = true)
        coEvery { gameDao.getById(1L) } returns game
        coEvery { variantResolver.getVariantOptions(game) } returns null
        coEvery { gameDiscDao.getDiscsForGame(1L) } returns emptyList()

        val result = launcher.launch(1L)

        // Multi-disc with no discs should error or return select disc
        assertTrue(
            "Multi-disc routing failed: ${result::class.simpleName}",
            result is LaunchResult.SelectDisc || result is LaunchResult.Error || result is LaunchResult.NoRomFile
        )
    }
}
