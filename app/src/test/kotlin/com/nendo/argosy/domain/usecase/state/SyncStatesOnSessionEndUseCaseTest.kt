package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DiscoveredState
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * With Secure Saves off, states written outside a session are adopted, except a slot whose file is
 * byte-identical to the cached state for that slot: that file is one Argosy put there, and adopting
 * it would upload a second server copy of the same state.
 */
class SyncStatesOnSessionEndUseCaseTest {

    private val stateCacheManager = mockk<StateCacheManager>(relaxed = true)
    private val gameDao = mockk<GameDao>()
    private val emulatorDetector = mockk<EmulatorDetector>(relaxed = true) {
        every { getByPackage(any()) } answers {
            com.nendo.argosy.data.emulator.EmulatorRegistry.getByPackage(firstArg())
        }
    }
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val preferencesRepository = mockk<UserPreferencesRepository>()
    private val ownershipTracker = mockk<StateOwnershipTracker>()
    private val accountSwitchMarkerStore = mockk<AccountSwitchMarkerStore>()
    private val serialResolver = mockk<StateSerialResolver>()
    private val fileAccessLayer = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true)
    private val preferences = MutableStateFlow(UserPreferences(secureSaves = false))

    private val file = File("slot1.state1")
    private val existing = StateCacheEntity(
        id = 11L,
        gameId = GAME_ID,
        platformSlug = "snes",
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        slotNumber = 1,
        cachedAt = Instant.ofEpochSecond(1_000),
        stateSize = 1,
        cachePath = "cached"
    )

    private lateinit var useCase: SyncStatesOnSessionEndUseCase

    @Before
    fun setUp() {
        val game = mockk<GameEntity> {
            every { id } returns GAME_ID
            every { platformId } returns PLATFORM_ID
            every { localPath } returns "/roms/game.sfc"
            every { platformSlug } returns "snes"
            every { rommId } returns null
        }
        coEvery { gameDao.getById(GAME_ID) } returns game
        every { preferencesRepository.userPreferences } returns preferences
        coEvery { emulatorConfigDao.getByGameId(any()) } returns null
        coEvery { emulatorConfigDao.getDefaultForPlatform(any()) } returns null
        every { emulatorDetector.installedEmulators } returns MutableStateFlow(emptyList())
        every { emulatorDetector.getPreferredEmulator(any(), any()) } returns null
        coEvery { ownershipTracker.claim(any(), any()) } returns StateClaim.Mine
        coEvery { accountSwitchMarkerStore.isSwitching() } returns false
        coEvery {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any(), any())
        } returns listOf(DiscoveredState(file, slotNumber = 1, lastModified = Instant.ofEpochSecond(2_000)))
        coEvery { serialResolver.serialsFor(any(), any()) } returns emptySet()
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing
        coEvery {
            stateCacheManager.cacheState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns 42L

        useCase = SyncStatesOnSessionEndUseCase(
            stateCacheManager = stateCacheManager,
            gameDao = gameDao,
            activeSaveRepository = mockk(relaxed = true),
            coreVersionExtractor = mockk(relaxed = true),
            preferencesRepository = preferencesRepository,
            stateOwnershipTracker = ownershipTracker,
            emulatorResolver = EmulatorResolver(
                emulatorDetector = emulatorDetector,
                emulatorConfigDao = emulatorConfigDao,
                userPreferencesRepository = preferencesRepository,
                libretroCoreMgr = mockk(relaxed = true),
                installedAppResolver = mockk(relaxed = true) { every { isAppInstalled(any()) } returns false }
            ),
            accountSwitchMarkerStore = accountSwitchMarkerStore,
            stateSerialResolver = serialResolver,
            fileAccessLayer = fileAccessLayer
        )
    }

    @Test
    fun `session end files states under the emulator that ran, not the configured one`() = runTest {
        val retroarch = EmulatorRegistry.getById("retroarch")!!
        coEvery { emulatorConfigDao.getByGameId(GAME_ID) } returns EmulatorConfigEntity(
            platformId = PLATFORM_ID,
            gameId = GAME_ID,
            packageName = EmulatorRegistry.BUILTIN_PACKAGE,
            displayName = null,
            coreName = null
        )

        useCase(GAME_ID, retroarch.packageName, queueUploads = false)

        coVerify {
            stateCacheManager.discoverStatesForGame(GAME_ID, "retroarch", any(), "snes", any(), retroarch.packageName, any())
        }
    }

    @Test
    fun `a RetroArch fork session files states under the retroarch family id`() = runTest {
        val forkPackage = "com.retroarch.aarch64.nightly"

        useCase(GAME_ID, forkPackage, queueUploads = false)

        coVerify {
            stateCacheManager.discoverStatesForGame(GAME_ID, "retroarch", any(), "snes", any(), forkPackage, any())
        }
    }

    @Test
    fun `a session with no recorded package files states under the emulator the launch resolves`() = runTest {
        val retroarch = EmulatorRegistry.getById("retroarch")!!
        val installed = InstalledEmulator(def = retroarch, versionName = "1.0", versionCode = 1L)
        every { emulatorDetector.installedEmulators } returns MutableStateFlow(listOf(installed))
        every { emulatorDetector.getPreferredEmulator("snes", any()) } returns installed

        useCase(GAME_ID, "", queueUploads = false)

        coVerify {
            stateCacheManager.discoverStatesForGame(GAME_ID, "retroarch", any(), "snes", any(), retroarch.packageName, any())
        }
    }

    @Test
    fun `off-session state matching the cached slot is not adopted`() = runTest {
        coEvery { stateCacheManager.hasSameContent(existing, file) } returns true

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) {
            stateCacheManager.cacheState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `off-session state that differs from the cached slot is adopted`() = runTest {
        coEvery { stateCacheManager.hasSameContent(existing, file) } returns false

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.Cached(1, 0), result)
        coVerify { stateCacheManager.markForUpload(42L) }
    }

    @Test
    fun `an off-session state older than the server copy uploads as its own state`() = runTest {
        coEvery { stateCacheManager.hasSameContent(any(), any()) } returns false
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing.copy(
            rommSaveId = 99L,
            serverUpdatedAt = Instant.ofEpochSecond(3_000)
        )

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify { stateCacheManager.clearServerLink(existing.id) }
    }

    @Test
    fun `an off-session state newer than the server copy keeps its server link`() = runTest {
        coEvery { stateCacheManager.hasSameContent(any(), any()) } returns false
        coEvery { stateCacheManager.getStateBySlot(GAME_ID, any(), 1, any()) } returns existing.copy(
            rommSaveId = 99L,
            serverUpdatedAt = Instant.ofEpochSecond(1_500)
        )

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.clearServerLink(any()) }
    }

    @Test
    fun `a slot no newer than its cache is not hashed`() = runTest {
        coEvery {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any(), any())
        } returns listOf(DiscoveredState(file, slotNumber = 1, lastModified = Instant.ofEpochSecond(500)))

        useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.hasSameContent(any(), any()) }
    }

    @Test
    fun `nothing is adopted during an account switch`() = runTest {
        coEvery { accountSwitchMarkerStore.isSwitching() } returns true

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.NotConfigured, result)
        coVerify(exactly = 0) {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `nothing is adopted while Secure Saves is on`() = runTest {
        preferences.value = UserPreferences(secureSaves = true)

        val result = useCase.adoptOffSessionStates(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        assertEquals(StateSyncResult.NotConfigured, result)
        coVerify(exactly = 0) {
            stateCacheManager.discoverStatesForGame(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `the game's trusted serials reach the repair and the discovery`() = runTest {
        coEvery { serialResolver.serialsFor(GAME_ID, any()) } returns setOf("SLUS-00001")

        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify { stateCacheManager.detachForeignSerialStates(GAME_ID, setOf("SLUS-00001")) }
        coVerify {
            stateCacheManager.discoverStatesForGame(GAME_ID, any(), any(), any(), setOf("SLUS-00001"), any(), any())
        }
    }

    @Test
    fun `discovered state files are opened for reading before they are cached`() = runTest {
        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        io.mockk.coVerifyOrder {
            fileAccessLayer.prepareSaveAccess(*anyVararg())
            stateCacheManager.cacheState(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `session end still caches a newer slot without comparing content`() = runTest {
        useCase(GAME_ID, EmulatorRegistry.BUILTIN_PACKAGE, queueUploads = false)

        coVerify(exactly = 0) { stateCacheManager.hasSameContent(any(), any()) }
        coVerify { stateCacheManager.markForUpload(42L) }
    }

    private companion object {
        const val GAME_ID = 5L
        const val PLATFORM_ID = 9L
    }
}
