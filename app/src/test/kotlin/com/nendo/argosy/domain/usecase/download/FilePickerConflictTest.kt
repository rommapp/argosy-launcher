package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.download.DownloadEnqueueResult
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.FilePickerRow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FilePickerConflictTest {
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val gameFileDao = mockk<GameFileDao>(relaxed = true)
    private val downloadGame = mockk<DownloadGameUseCase>()
    private val manager = mockk<DownloadManager>()
    private val useCase = FilePickerFlowUseCase(
        gameDao, gameFileDao, mockk(relaxed = true), mockk(relaxed = true), downloadGame,
        manager, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
    )

    @Test
    fun `chosen download conflict returns issue and zero queued`() = runTest {
        val error = DownloadResult.Error(DownloadGameFailureReason.RequestConflict)
        coEvery { downloadGame(1L, listOf(7L)) } returns error

        assertEquals(0 to listOf(error), useCase.downloadSelection(1L, setOf(7L)))
    }

    @Test
    fun `managed download conflict returns issue and zero added`() = runTest {
        val game = mockk<GameEntity>(relaxed = true)
        coEvery { game.localPath } returns null
        coEvery { gameDao.getById(1L) } returns game
        coEvery { gameFileDao.getFilesForGame(1L) } returns listOf(
            GameFileEntity(id = 2L, gameId = 1L, rommFileId = 7L, fileName = "addon.bin", filePath = "", category = "game", fileSize = 10L)
        )
        coEvery { manager.enqueueGameFileDownload(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns DownloadEnqueueResult.Conflict(9L)
        val rows = listOf(FilePickerRow(isHeader = false, groupKey = "game", label = "addon.bin", rommFileId = 7L))

        assertEquals(
            ManagedSelectionResult(0, 0, listOf(DownloadGameFailureReason.RequestConflict)),
            useCase.applyManagedSelection(1L, rows, setOf(7L))
        )
    }
}
