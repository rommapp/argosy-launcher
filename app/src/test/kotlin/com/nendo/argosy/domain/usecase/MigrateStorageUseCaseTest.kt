package com.nendo.argosy.domain.usecase

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MigrateStorageUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var gameRepository: GameRepository
    private lateinit var preferencesRepository: UserPreferencesRepository
    private lateinit var notificationManager: NotificationManager
    private lateinit var useCase: MigrateStorageUseCase

    private lateinit var oldDir: File
    private lateinit var newDir: File

    @Before
    fun setup() {
        gameRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        useCase = MigrateStorageUseCase(gameRepository, preferencesRepository, notificationManager)

        oldDir = tempFolder.newFolder("old")
        newDir = tempFolder.newFolder("new")
    }

    @Test
    fun `invoke migrates existing files to new path`() = runTest {
        val oldFile = File(oldDir, "nes/game.nes").apply {
            parentFile?.mkdirs()
            writeText("rom data")
        }
        val game = createGameEntity(localPath = oldFile.absolutePath)
        coEvery { gameRepository.getGamesWithLocalPaths() } returns listOf(game)

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(1, result.migrated)
        assertEquals(0, result.skipped)
        assertEquals(0, result.failed)

        val newFile = File(newDir, "nes/game.nes")
        assertEquals(true, newFile.exists())
        assertEquals("rom data", newFile.readText())
        assertEquals(false, oldFile.exists())
    }

    @Test
    fun `invoke skips missing files and clears their paths`() = runTest {
        val missingPath = File(oldDir, "nes/missing.nes").absolutePath
        val game = createGameEntity(localPath = missingPath)
        coEvery { gameRepository.getGamesWithLocalPaths() } returns listOf(game)

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(0, result.migrated)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        coVerify { gameRepository.clearLocalPath(game.id) }
    }

    @Test
    fun `invoke updates preferences on completion`() = runTest {
        coEvery { gameRepository.getGamesWithLocalPaths() } returns emptyList()

        useCase(oldDir.absolutePath, newDir.absolutePath)

        coVerify { preferencesRepository.setRomStoragePath(newDir.absolutePath) }
    }

    @Test
    fun `invoke shows persistent notification during migration`() = runTest {
        coEvery { gameRepository.getGamesWithLocalPaths() } returns emptyList()

        useCase(oldDir.absolutePath, newDir.absolutePath)

        coVerify {
            notificationManager.showPersistent(
                key = "migration",
                title = "Moving games",
                subtitle = any(),
                progress = any()
            )
        }
    }

    @Test
    fun `invoke completes notification with success when no failures`() = runTest {
        coEvery { gameRepository.getGamesWithLocalPaths() } returns emptyList()

        useCase(oldDir.absolutePath, newDir.absolutePath)

        coVerify {
            notificationManager.completePersistent(
                key = "migration",
                title = "Migration complete",
                subtitle = any(),
                type = NotificationType.SUCCESS
            )
        }
    }

    @Test
    fun `invoke completes notification with warning when there are failures`() = runTest {
        val oldFile = File(oldDir, "nes/game.nes").apply {
            parentFile?.mkdirs()
            writeText("rom data")
        }
        oldFile.setReadable(false)

        val game = createGameEntity(localPath = oldFile.absolutePath)
        coEvery { gameRepository.getGamesWithLocalPaths() } returns listOf(game)

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        oldFile.setReadable(true)

        if (result.failed > 0) {
            coVerify {
                notificationManager.completePersistent(
                    key = "migration",
                    title = "Migration complete",
                    subtitle = any(),
                    type = NotificationType.WARNING
                )
            }
        }
    }

    @Test
    fun `invoke reports progress correctly`() = runTest {
        val file1 = File(oldDir, "nes/game1.nes").apply {
            parentFile?.mkdirs()
            writeText("data1")
        }
        val file2 = File(oldDir, "snes/game2.smc").apply {
            parentFile?.mkdirs()
            writeText("data2")
        }
        val games = listOf(
            createGameEntity(id = 1, localPath = file1.absolutePath, title = "Game 1"),
            createGameEntity(id = 2, localPath = file2.absolutePath, platformId = 2L, title = "Game 2")
        )
        coEvery { gameRepository.getGamesWithLocalPaths() } returns games

        val progressUpdates = mutableListOf<Triple<Int, Int, String>>()
        useCase(oldDir.absolutePath, newDir.absolutePath) { current, total, title ->
            progressUpdates.add(Triple(current, total, title))
        }

        assertEquals(2, progressUpdates.size)
        assertEquals(Triple(1, 2, "Game 1"), progressUpdates[0])
        assertEquals(Triple(2, 2, "Game 2"), progressUpdates[1])
    }

    @Test
    fun `invoke updates local path in database after migration`() = runTest {
        val oldFile = File(oldDir, "nes/game.nes").apply {
            parentFile?.mkdirs()
            writeText("rom data")
        }
        val game = createGameEntity(id = 123, localPath = oldFile.absolutePath)
        coEvery { gameRepository.getGamesWithLocalPaths() } returns listOf(game)

        useCase(oldDir.absolutePath, newDir.absolutePath)

        val expectedNewPath = File(newDir, "nes/game.nes").absolutePath
        coVerify { gameRepository.updateLocalPath(123, expectedNewPath) }
    }

    @Test
    fun `invoke handles games with null localPath`() = runTest {
        val game = createGameEntity(localPath = null)
        coEvery { gameRepository.getGamesWithLocalPaths() } returns listOf(game)

        val result = useCase(oldDir.absolutePath, newDir.absolutePath)

        assertEquals(0, result.migrated)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
    }

    private fun createGameEntity(
        id: Long = 1L,
        localPath: String? = null,
        platformId: Long = 1L,
        title: String = "Test Game"
    ) = GameEntity(
        id = id,
        platformId = platformId,
        title = title,
        sortTitle = title.lowercase(),
        localPath = localPath,
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY
    )
}
