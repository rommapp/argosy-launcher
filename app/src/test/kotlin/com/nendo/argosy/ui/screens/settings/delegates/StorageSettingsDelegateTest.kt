package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DatabaseAdminRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.domain.usecase.MigratePlatformStorageUseCase
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.domain.usecase.PurgePlatformUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import com.nendo.argosy.ui.screens.settings.StorageState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var platformRepository: PlatformRepository
    private lateinit var migrateStorageUseCase: MigrateStorageUseCase
    private lateinit var migratePlatformStorageUseCase: MigratePlatformStorageUseCase
    private lateinit var purgePlatformUseCase: PurgePlatformUseCase
    private lateinit var syncPlatformUseCase: SyncPlatformUseCase
    private lateinit var platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue
    private lateinit var databaseAdminRepository: DatabaseAdminRepository
    private lateinit var delegate: StorageSettingsDelegate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        preferencesRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        platformRepository = mockk(relaxed = true)
        migrateStorageUseCase = mockk(relaxed = true)
        migratePlatformStorageUseCase = mockk(relaxed = true)
        purgePlatformUseCase = mockk(relaxed = true)
        syncPlatformUseCase = mockk(relaxed = true)
        platformSyncQueue = mockk(relaxed = true)
        databaseAdminRepository = mockk(relaxed = true)

        delegate = delegateWith(context = mockk(relaxed = true), platformFolderResolver = mockk(relaxed = true))
    }

    private fun delegateWith(context: Context, platformFolderResolver: PlatformFolderResolver) =
        StorageSettingsDelegate(
            context = context,
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            platformRepository = platformRepository,
            migrateStorageUseCase = migrateStorageUseCase,
            migratePlatformStorageUseCase = migratePlatformStorageUseCase,
            purgePlatformUseCase = purgePlatformUseCase,
            syncPlatformUseCase = syncPlatformUseCase,
            platformSyncQueue = platformSyncQueue,
            databaseAdminRepository = databaseAdminRepository,
            saveCacheRepository = mockk(relaxed = true),
            notificationManager = mockk(relaxed = true),
            platformFolderResolver = platformFolderResolver
        )

    private val appContext: Context = mockk(relaxed = true) {
        every { filesDir } returns File("/data/user/0/com.nendo.argosy/files")
        every { cacheDir } returns File("/data/user/0/com.nendo.argosy/cache")
        every { dataDir } returns File("/data/user/0/com.nendo.argosy")
        every { externalCacheDir } returns File("/storage/emulated/0/Android/data/com.nendo.argosy/cache")
        every { getExternalFilesDir(null) } returns File("/storage/emulated/0/Android/data/com.nendo.argosy/files")
    }

    private val snes = PlatformEntity(
        id = 4L,
        slug = "snes",
        name = "Super Nintendo",
        shortName = "SNES",
        romExtensions = "sfc"
    )

    private fun resolverWithoutGlobalPath(): PlatformFolderResolver {
        val preferences = mockk<UserPreferences>(relaxed = true) {
            every { romStoragePath } returns null
        }
        every { preferencesRepository.userPreferences } returns flowOf(preferences)
        return PlatformFolderResolver(appContext, mockk(relaxed = true), mockk(relaxed = true), preferencesRepository)
    }

    @Test
    fun `platform configs with no global path show no folder`() = testScope.runTest {
        val resolver = resolverWithoutGlobalPath()
        every { platformRepository.observeConfigurablePlatforms() } returns flowOf(listOf(snes))
        delegate = delegateWith(appContext, resolver)
        delegate.updateState(StorageState(romStoragePath = ""))

        delegate.loadPlatformConfigs(this)
        advanceUntilIdle()

        assertEquals("", delegate.state.value.platformConfigs.single().effectivePath)
    }

    @Test
    fun `choosing a platform folder with no global path applies it without a migration`() = testScope.runTest {
        val resolver = resolverWithoutGlobalPath()
        coEvery { platformRepository.getById(snes.id) } returns snes
        coEvery { gameRepository.getGamesWithLocalPathsForPlatform(snes.id) } returns listOf(mockk(relaxed = true))
        delegate = delegateWith(appContext, resolver)
        delegate.updateState(StorageState(romStoragePath = ""))

        delegate.setPlatformPath(this, snes.id, "/storage/emulated/0/Roms/snes")
        advanceUntilIdle()

        assertEquals(null, delegate.state.value.showMigratePlatformConfirm)
        coVerify(exactly = 1) { platformRepository.updateCustomRomPath(snes.id, "/storage/emulated/0/Roms/snes") }
    }

    @Test
    fun `resetting a platform with no global path keeps its own folder`() = testScope.runTest {
        val resolver = resolverWithoutGlobalPath()
        val custom = snes.copy(customRomPath = "/storage/emulated/0/Roms/snes")
        every { platformRepository.observeConfigurablePlatforms() } returns flowOf(listOf(custom))
        coEvery { platformRepository.getById(snes.id) } returns custom
        coEvery { gameRepository.getGamesWithLocalPathsForPlatform(snes.id) } returns listOf(mockk(relaxed = true))
        delegate = delegateWith(appContext, resolver)
        delegate.updateState(StorageState(romStoragePath = ""))
        delegate.loadPlatformConfigs(this)
        advanceUntilIdle()

        delegate.resetPlatformToGlobal(this, snes.id)
        advanceUntilIdle()

        assertEquals(null, delegate.state.value.showMigratePlatformConfirm)
        assertEquals(
            "/storage/emulated/0/Roms/snes",
            delegate.state.value.platformConfigs.single().effectivePath
        )
        coVerify(exactly = 0) { platformRepository.updateCustomRomPath(any(), any()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cycleInstantDownloadThreshold cycles from 50 to 100`() = testScope.runTest {
        delegate.updateState(StorageState(instantDownloadThresholdMb = 50))

        delegate.cycleInstantDownloadThreshold(this)
        advanceUntilIdle()

        assertEquals(100, delegate.state.value.instantDownloadThresholdMb)
        coVerify { preferencesRepository.setInstantDownloadThresholdMb(100) }
    }

    @Test
    fun `cycleInstantDownloadThreshold cycles from 100 to 250`() = testScope.runTest {
        delegate.updateState(StorageState(instantDownloadThresholdMb = 100))

        delegate.cycleInstantDownloadThreshold(this)
        advanceUntilIdle()

        assertEquals(250, delegate.state.value.instantDownloadThresholdMb)
        coVerify { preferencesRepository.setInstantDownloadThresholdMb(250) }
    }

    @Test
    fun `cycleInstantDownloadThreshold cycles from 250 to 500`() = testScope.runTest {
        delegate.updateState(StorageState(instantDownloadThresholdMb = 250))

        delegate.cycleInstantDownloadThreshold(this)
        advanceUntilIdle()

        assertEquals(500, delegate.state.value.instantDownloadThresholdMb)
        coVerify { preferencesRepository.setInstantDownloadThresholdMb(500) }
    }

    @Test
    fun `cycleInstantDownloadThreshold cycles from 500 back to 50`() = testScope.runTest {
        delegate.updateState(StorageState(instantDownloadThresholdMb = 500))

        delegate.cycleInstantDownloadThreshold(this)
        advanceUntilIdle()

        assertEquals(50, delegate.state.value.instantDownloadThresholdMb)
        coVerify { preferencesRepository.setInstantDownloadThresholdMb(50) }
    }

    @Test
    fun `cycleInstantDownloadThreshold handles unknown value by going to 100`() = testScope.runTest {
        delegate.updateState(StorageState(instantDownloadThresholdMb = 75))

        delegate.cycleInstantDownloadThreshold(this)
        advanceUntilIdle()

        assertEquals(100, delegate.state.value.instantDownloadThresholdMb)
    }

    @Test
    fun `toggling remote installs off stores and shows off`() = testScope.runTest {
        delegate.updateState(StorageState(allowRemoteInstalls = true))

        delegate.toggleAllowRemoteInstalls(this)
        advanceUntilIdle()

        assertFalse(delegate.state.value.allowRemoteInstalls)
        coVerify(exactly = 1) { preferencesRepository.setAllowRemoteInstalls(false) }
    }

    @Test
    fun `toggling remote installs on stores and shows on`() = testScope.runTest {
        delegate.updateState(StorageState(allowRemoteInstalls = false))

        delegate.toggleAllowRemoteInstalls(this)
        advanceUntilIdle()

        assertTrue(delegate.state.value.allowRemoteInstalls)
        coVerify(exactly = 1) { preferencesRepository.setAllowRemoteInstalls(true) }
    }

    @Test
    fun `toggling auto start downloads off stores and shows off`() = testScope.runTest {
        delegate.updateState(StorageState(autoStartDownloads = true))

        delegate.toggleAutoStartDownloads(this)
        advanceUntilIdle()

        assertFalse(delegate.state.value.autoStartDownloads)
        coVerify(exactly = 1) { preferencesRepository.setAutoStartDownloads(false) }
    }

    @Test
    fun `toggling auto start downloads on stores and shows on`() = testScope.runTest {
        delegate.updateState(StorageState(autoStartDownloads = false))

        delegate.toggleAutoStartDownloads(this)
        advanceUntilIdle()

        assertTrue(delegate.state.value.autoStartDownloads)
        coVerify(exactly = 1) { preferencesRepository.setAutoStartDownloads(true) }
    }

    @Test
    fun `full cycle through all threshold values`() = testScope.runTest {
        val expectedSequence = listOf(50, 100, 250, 500, 50)
        delegate.updateState(StorageState(instantDownloadThresholdMb = 50))

        for (i in 1 until expectedSequence.size) {
            delegate.cycleInstantDownloadThreshold(this)
            advanceUntilIdle()
            assertEquals(
                "After cycle $i, expected ${expectedSequence[i]}",
                expectedSequence[i],
                delegate.state.value.instantDownloadThresholdMb
            )
        }
    }
}
