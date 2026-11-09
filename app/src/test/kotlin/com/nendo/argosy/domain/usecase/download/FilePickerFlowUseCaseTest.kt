package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.emulator.TexturePackInstaller
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.data.music.MusicDirectoryManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePickerFlowUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val manifestPath =
        "/storage/emulated/0/Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01/.argosy_mod_7.manifest"

    private lateinit var gameDao: GameDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var texturePackInstaller: TexturePackInstaller
    private lateinit var useCase: FilePickerFlowUseCase

    private val packRow = GameFileEntity(
        id = 7L,
        gameId = 1L,
        rommFileId = 507L,
        romId = 100L,
        fileName = "hd_textures.zip",
        filePath = "mods/hd_textures.zip",
        category = "mod",
        fileSize = 1024L,
        localPath = manifestPath,
        isLaunchTarget = false
    )
    private val pickerRow = FilePickerRow(isHeader = false, groupKey = "mod", label = "hd_textures.zip", rommFileId = 507L)

    @Before
    fun setup() {
        gameDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)
        texturePackInstaller = mockk(relaxed = true)
        val musicDirectoryManager = mockk<MusicDirectoryManager>(relaxed = true)
        coEvery { musicDirectoryManager.resolveMusicDir() } returns tempFolder.newFolder("music")
        coEvery { gameDao.getById(1L) } returns null
        coEvery { gameFileDao.getFilesForGame(1L) } returns listOf(packRow)
        every { texturePackInstaller.manifestExists(manifestPath) } returns true
        coEvery { texturePackInstaller.uninstallModFile(any()) } returns true

        useCase = FilePickerFlowUseCase(
            gameDao = gameDao,
            gameFileDao = gameFileDao,
            romMRepository = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            downloadGameUseCase = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true),
            bgmPlaylistRepository = mockk(relaxed = true),
            musicDirectoryManager = musicDirectoryManager,
            controlsPreferencesRepository = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            gameRepository = mockk(relaxed = true),
            volumeHealth = mockk(relaxed = true),
            m3uManager = mockk(relaxed = true),
            texturePackInstaller = texturePackInstaller
        )
    }

    @Test
    fun `unticking a pack installed under Android data uninstalls it`() = runTest {
        val result = useCase.applyManagedSelection(1L, listOf(pickerRow), selected = emptySet())

        assertEquals(0 to 1, result)
        coVerify { texturePackInstaller.uninstallModFile(match { it.localPath == File(manifestPath).absolutePath }) }
        coVerify { gameFileDao.clearLocalPath(7L) }
    }

    @Test
    fun `keeping a pack installed under Android data ticked queues no second download`() = runTest {
        val result = useCase.applyManagedSelection(1L, listOf(pickerRow), selected = setOf(507L))

        assertEquals(0 to 0, result)
    }
}
