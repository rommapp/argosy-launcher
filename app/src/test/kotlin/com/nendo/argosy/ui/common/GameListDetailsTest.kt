package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.toListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.domain.model.CompletionProgress
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameListDetailsTest {

    private val downloadStatus = mockk<DownloadFileStatusRepository>(relaxed = true)

    private val expected = GameListDetails(
        releaseYear = 1995,
        genre = "RPG",
        rating = 92f,
        userRating = 9,
        userDifficulty = 4,
        achievementCount = 40,
        earnedAchievementCount = 12,
        completion = CompletionProgress.UserSet(65),
        playTimeMinutes = 600,
        timeToBeatMainSec = 90_000,
        igdbId = 1234L
    )

    private val listItem = GameListItem(
        id = 10L,
        platformId = 1L,
        platformSlug = "snes",
        title = "Chrono Trigger",
        sortTitle = "chrono trigger",
        localPath = "/roms/ct.sfc",
        source = GameSource.ROMM_SYNCED,
        isFavorite = false,
        isHidden = false,
        isMultiDisc = false,
        rommId = 99L,
        steamAppId = null,
        packageName = null,
        steamLauncher = null,
        playCount = 3,
        playTimeMinutes = 600,
        lastPlayed = null,
        genre = "RPG",
        players = null,
        rating = 92f,
        userRating = 9,
        userDifficulty = 4,
        releaseYear = 1995,
        addedAt = Instant.EPOCH,
        achievementCount = 40,
        earnedAchievementCount = 12,
        completion = 65,
        status = "finished",
        developer = "Square",
        igdbId = 1234L,
        timeToBeatMainSec = 90_000
    )

    private val entity = GameEntity(
        id = 10L,
        platformId = 1L,
        platformSlug = "snes",
        title = "Chrono Trigger",
        sortTitle = "chrono trigger",
        localPath = "/roms/ct.sfc",
        rommId = 99L,
        igdbId = 1234L,
        source = GameSource.ROMM_SYNCED,
        developer = "Square",
        releaseYear = 1995,
        genre = "RPG",
        rating = 92f,
        userRating = 9,
        userDifficulty = 4,
        achievementCount = 40,
        earnedAchievementCount = 12,
        completion = 65,
        status = "finished",
        playTimeMinutes = 600,
        timeToBeatMainSec = 90_000
    )

    @Test
    fun `a projected row carries every list field into the details`() {
        assertEquals(expected, listItem.listDetails)
    }

    @Test
    fun `an entity carries the same details as its projection`() {
        assertEquals(listItem.listDetails, entity.listDetails)
    }

    @Test
    fun `a multi-genre row carries only its first genre`() {
        assertEquals("Action", listItem.copy(genre = "Action, Role-playing (RPG)").listDetails.genre)
        assertEquals("Action", entity.copy(genre = " Action ,Adventure").listDetails.genre)
    }

    @Test
    fun `a blank genre carries no genre`() {
        assertNull(listItem.copy(genre = " , RPG").listDetails.genre)
        assertNull(entity.copy(genre = null).listDetails.genre)
    }

    @Test
    fun `the library ui model carries the projected details`() = runTest {
        assertEquals(expected, listItem.toLibraryGameUi(downloadStatus, art = null).listDetails)
    }

    @Test
    fun `a collection row shows earned and total achievements`() = runTest {
        val row = entity.toListItem(isHidden = false).toLibraryGameUi(downloadStatus, art = null)

        assertEquals(12, row.listDetails.earnedAchievementCount)
        assertEquals(40, row.listDetails.achievementCount)
    }

    @Test
    fun `a collection row reads downloaded state through the shared resolver`() = runTest {
        val androidApp = entity.copy(localPath = null, source = GameSource.ANDROID_APP)

        assertTrue(androidApp.toListItem(isHidden = false).toLibraryGameUi(downloadStatus, art = null).isDownloaded)
    }

    @Test
    fun `an entity converted for a collection matches its projection`() {
        assertEquals(
            listItem.copy(playCount = 0, addedAt = entity.addedAt),
            entity.toListItem(isHidden = false)
        )
    }
}
