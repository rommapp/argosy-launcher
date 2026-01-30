package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.storage.ManagedStorageAccessor
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.MigratePlatformStorageUseCase
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.domain.usecase.PurgePlatformUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import com.nendo.argosy.ui.screens.settings.StorageState
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var gameRepository: GameRepository
    private lateinit var platformDao: PlatformDao
    private lateinit var gameDao: GameDao
    private lateinit var migrateStorageUseCase: MigrateStorageUseCase
    private lateinit var migratePlatformStorageUseCase: MigratePlatformStorageUseCase
    private lateinit var purgePlatformUseCase: PurgePlatformUseCase
    private lateinit var syncPlatformUseCase: SyncPlatformUseCase
    private lateinit var database: ALauncherDatabase
    private lateinit var imageCacheManager: ImageCacheManager
    private lateinit var managedStorageAccessor: ManagedStorageAccessor
    private lateinit var delegate: StorageSettingsDelegate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        preferencesRepository = mockk(relaxed = true)
        gameRepository = mockk(relaxed = true)
        platformDao = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        migrateStorageUseCase = mockk(relaxed = true)
        migratePlatformStorageUseCase = mockk(relaxed = true)
        purgePlatformUseCase = mockk(relaxed = true)
        syncPlatformUseCase = mockk(relaxed = true)
        database = mockk(relaxed = true)
        imageCacheManager = mockk(relaxed = true)
        managedStorageAccessor = mockk(relaxed = true)

        delegate = StorageSettingsDelegate(
            preferencesRepository = preferencesRepository,
            gameRepository = gameRepository,
            platformDao = platformDao,
            gameDao = gameDao,
            migrateStorageUseCase = migrateStorageUseCase,
            migratePlatformStorageUseCase = migratePlatformStorageUseCase,
            purgePlatformUseCase = purgePlatformUseCase,
            syncPlatformUseCase = syncPlatformUseCase,
            database = database,
            imageCacheManager = imageCacheManager,
            managedStorageAccessor = managedStorageAccessor
        )
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
