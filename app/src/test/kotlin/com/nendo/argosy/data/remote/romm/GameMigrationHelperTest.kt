package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameMigrationHelperTest {

    private fun createGameEntity(
        id: Long = 0,
        title: String = "Test Game",
        rommId: Long? = null,
        igdbId: Long? = 12345,
        platformId: Long = 1,
        localPath: String? = null,
        playCount: Int = 0,
        playTimeMinutes: Int = 0,
        userRating: Int = 0,
        userDifficulty: Int = 0,
        completion: Int = 0,
        isFavorite: Boolean = false,
        isHidden: Boolean = false,
        addedAt: Instant = Instant.now(),
        lastPlayed: Instant? = null,
        achievementCount: Int = 0
    ) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = "psx",
        title = title,
        sortTitle = title,
        localPath = localPath,
        rommId = rommId,
        igdbId = igdbId,
        source = GameSource.ROMM_REMOTE,
        coverPath = null,
        backgroundPath = null,
        screenshotPaths = null,
        description = null,
        releaseYear = null,
        genre = null,
        developer = null,
        rating = null,
        regions = null,
        languages = null,
        gameModes = null,
        franchises = null,
        userRating = userRating,
        userDifficulty = userDifficulty,
        completion = completion,
        status = null,
        backlogged = false,
        nowPlaying = false,
        isFavorite = isFavorite,
        isHidden = isHidden,
        isMultiDisc = false,
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        lastPlayed = lastPlayed,
        addedAt = addedAt,
        achievementCount = achievementCount
    )

    @Test
    fun `empty list returns null`() {
        val result = GameMigrationHelper.aggregateMultiDiscData(emptyList())
        assertNull(result)
    }

    @Test
    fun `single source returns that source unchanged`() {
        val source = createGameEntity(
            id = 100,
            title = "Final Fantasy VII (Disc 1)",
            rommId = 1,
            playCount = 5,
            playTimeMinutes = 120
        )

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(source))

        assertEquals(source, result)
    }

    @Test
    fun `multiple sources sums play counts`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, playCount = 10)
        val disc2 = createGameEntity(id = 2, rommId = 101, playCount = 5)
        val disc3 = createGameEntity(id = 3, rommId = 102, playCount = 3)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(18, result.playCount)
    }

    @Test
    fun `multiple sources sums play time`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, playTimeMinutes = 600)
        val disc2 = createGameEntity(id = 2, rommId = 101, playTimeMinutes = 300)
        val disc3 = createGameEntity(id = 3, rommId = 102, playTimeMinutes = 180)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(1080, result.playTimeMinutes)
    }

    @Test
    fun `multiple sources takes max user rating`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, userRating = 3)
        val disc2 = createGameEntity(id = 2, rommId = 101, userRating = 5)
        val disc3 = createGameEntity(id = 3, rommId = 102, userRating = 4)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(5, result.userRating)
    }

    @Test
    fun `multiple sources takes max difficulty`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, userDifficulty = 2)
        val disc2 = createGameEntity(id = 2, rommId = 101, userDifficulty = 4)
        val disc3 = createGameEntity(id = 3, rommId = 102, userDifficulty = 1)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(4, result.userDifficulty)
    }

    @Test
    fun `multiple sources takes max completion`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, completion = 50)
        val disc2 = createGameEntity(id = 2, rommId = 101, completion = 100)
        val disc3 = createGameEntity(id = 3, rommId = 102, completion = 75)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(100, result.completion)
    }

    @Test
    fun `isFavorite is true if any source is favorite`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, isFavorite = false)
        val disc2 = createGameEntity(id = 2, rommId = 101, isFavorite = true)
        val disc3 = createGameEntity(id = 3, rommId = 102, isFavorite = false)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertTrue(result.isFavorite)
    }

    @Test
    fun `isFavorite is false if no source is favorite`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, isFavorite = false)
        val disc2 = createGameEntity(id = 2, rommId = 101, isFavorite = false)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2))!!

        assertFalse(result.isFavorite)
    }

    @Test
    fun `isHidden is true only if all sources are hidden`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, isHidden = true)
        val disc2 = createGameEntity(id = 2, rommId = 101, isHidden = true)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2))!!

        assertTrue(result.isHidden)
    }

    @Test
    fun `isHidden is false if any source is not hidden`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, isHidden = true)
        val disc2 = createGameEntity(id = 2, rommId = 101, isHidden = false)
        val disc3 = createGameEntity(id = 3, rommId = 102, isHidden = true)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertFalse(result.isHidden)
    }

    @Test
    fun `takes earliest addedAt`() {
        val earliest = Instant.parse("2024-01-01T00:00:00Z")
        val middle = Instant.parse("2024-06-15T00:00:00Z")
        val latest = Instant.parse("2024-12-01T00:00:00Z")

        val disc1 = createGameEntity(id = 1, rommId = 100, addedAt = middle)
        val disc2 = createGameEntity(id = 2, rommId = 101, addedAt = latest)
        val disc3 = createGameEntity(id = 3, rommId = 102, addedAt = earliest)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(earliest, result.addedAt)
    }

    @Test
    fun `takes most recent lastPlayed`() {
        val earliest = Instant.parse("2024-01-01T00:00:00Z")
        val middle = Instant.parse("2024-06-15T00:00:00Z")
        val latest = Instant.parse("2024-12-01T00:00:00Z")

        val disc1 = createGameEntity(id = 1, rommId = 100, lastPlayed = earliest)
        val disc2 = createGameEntity(id = 2, rommId = 101, lastPlayed = latest)
        val disc3 = createGameEntity(id = 3, rommId = 102, lastPlayed = middle)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(latest, result.lastPlayed)
    }

    @Test
    fun `handles null lastPlayed values`() {
        val played = Instant.parse("2024-06-15T00:00:00Z")

        val disc1 = createGameEntity(id = 1, rommId = 100, lastPlayed = null)
        val disc2 = createGameEntity(id = 2, rommId = 101, lastPlayed = played)
        val disc3 = createGameEntity(id = 3, rommId = 102, lastPlayed = null)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(played, result.lastPlayed)
    }

    @Test
    fun `lastPlayed is null if all sources have null lastPlayed`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, lastPlayed = null)
        val disc2 = createGameEntity(id = 2, rommId = 101, lastPlayed = null)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2))!!

        assertNull(result.lastPlayed)
    }

    @Test
    fun `takes first non-null localPath`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = null)
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = "/storage/disc2.bin")
        val disc3 = createGameEntity(id = 3, rommId = 102, localPath = "/storage/disc3.bin")

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2, disc3),
            pathValidator = { true }
        )!!

        assertEquals("/storage/disc2.bin", result.localPath)
    }

    @Test
    fun `localPath is null if all sources have null localPath`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = null)
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = null)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2))!!

        assertNull(result.localPath)
    }

    @Test
    fun `takes max achievement count`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, achievementCount = 10)
        val disc2 = createGameEntity(id = 2, rommId = 101, achievementCount = 25)
        val disc3 = createGameEntity(id = 3, rommId = 102, achievementCount = 15)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2, disc3))!!

        assertEquals(25, result.achievementCount)
    }

    @Test
    fun `result id is always 0 for new entity creation`() {
        val disc1 = createGameEntity(id = 100, rommId = 1)
        val disc2 = createGameEntity(id = 200, rommId = 2)

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(disc1, disc2))!!

        assertEquals(0L, result.id)
    }

    @Test
    fun `migration preserves data when game has no updates or dlc`() {
        val addedTime = Instant.parse("2024-01-15T10:30:00Z")
        val lastPlayed = Instant.parse("2024-06-01T18:00:00Z")

        val flatGame = createGameEntity(
            id = 1,
            title = "Dragon Quest Builders 2",
            rommId = 100,
            igdbId = 56789,
            platformId = 20,
            localPath = "/storage/switch/DragonQuestBuilders2.xci",
            playCount = 25,
            playTimeMinutes = 3600,
            userRating = 5,
            userDifficulty = 2,
            completion = 75,
            isFavorite = true,
            isHidden = false,
            addedAt = addedTime,
            lastPlayed = lastPlayed,
            achievementCount = 0
        )

        val result = GameMigrationHelper.aggregateMultiDiscData(listOf(flatGame))!!

        assertEquals(flatGame, result)
        assertEquals("/storage/switch/DragonQuestBuilders2.xci", result.localPath)
        assertEquals(25, result.playCount)
        assertEquals(3600, result.playTimeMinutes)
        assertTrue(result.isFavorite)
    }

    @Test
    fun `migration handles downloaded discs with mixed local paths`() {
        val disc1 = createGameEntity(
            id = 1,
            rommId = 100,
            localPath = "/storage/psx/disc1.bin",
            playCount = 10,
            playTimeMinutes = 300
        )
        val disc2 = createGameEntity(
            id = 2,
            rommId = 101,
            localPath = "/storage/psx/disc2.bin",
            playCount = 8,
            playTimeMinutes = 250
        )
        val disc3 = createGameEntity(
            id = 3,
            rommId = 102,
            localPath = null,
            playCount = 2,
            playTimeMinutes = 50
        )

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2, disc3),
            pathValidator = { true }
        )!!

        assertEquals("/storage/psx/disc1.bin", result.localPath)
        assertEquals(20, result.playCount)
        assertEquals(600, result.playTimeMinutes)
    }

    @Test
    fun `migration preserves local path from any downloaded disc`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = null)
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = null)
        val disc3 = createGameEntity(id = 3, rommId = 102, localPath = "/storage/psx/disc3.bin")

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2, disc3),
            pathValidator = { true }
        )!!

        assertEquals("/storage/psx/disc3.bin", result.localPath)
    }

    @Test
    fun `path validation skips invalid paths and uses first valid one`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = "/invalid/path1.bin")
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = "/valid/path2.bin")
        val disc3 = createGameEntity(id = 3, rommId = 102, localPath = "/invalid/path3.bin")

        val validPaths = setOf("/valid/path2.bin")
        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2, disc3),
            pathValidator = { it in validPaths }
        )!!

        assertEquals("/valid/path2.bin", result.localPath)
    }

    @Test
    fun `path validation returns null when all paths are invalid`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = "/invalid/path1.bin")
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = "/invalid/path2.bin")

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2),
            pathValidator = { false }
        )!!

        assertNull(result.localPath)
    }

    @Test
    fun `path validation prefers earlier valid path over later valid path`() {
        val disc1 = createGameEntity(id = 1, rommId = 100, localPath = "/valid/first.bin")
        val disc2 = createGameEntity(id = 2, rommId = 101, localPath = "/valid/second.bin")

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2),
            pathValidator = { true }
        )!!

        assertEquals("/valid/first.bin", result.localPath)
    }

    @Test
    fun `path validation still aggregates other data when paths are invalid`() {
        val disc1 = createGameEntity(
            id = 1,
            rommId = 100,
            localPath = "/invalid/path.bin",
            playCount = 10,
            playTimeMinutes = 300,
            isFavorite = true
        )
        val disc2 = createGameEntity(
            id = 2,
            rommId = 101,
            localPath = "/also/invalid.bin",
            playCount = 5,
            playTimeMinutes = 150,
            userRating = 4
        )

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2),
            pathValidator = { false }
        )!!

        assertNull(result.localPath)
        assertEquals(15, result.playCount)
        assertEquals(450, result.playTimeMinutes)
        assertTrue(result.isFavorite)
        assertEquals(4, result.userRating)
    }

    @Test
    fun `psx multi-disc scenario - 3 discs consolidated into folder`() {
        val addedTime = Instant.parse("2024-01-15T10:30:00Z")
        val disc1Played = Instant.parse("2024-02-01T20:00:00Z")
        val disc2Played = Instant.parse("2024-02-10T18:00:00Z")
        val disc3Played = Instant.parse("2024-02-05T14:00:00Z")

        val disc1 = createGameEntity(
            id = 1,
            title = "Final Fantasy VII (Disc 1)",
            rommId = 100,
            igdbId = 427,
            platformId = 10,
            localPath = "/storage/psx/ff7_disc1.bin",
            playCount = 15,
            playTimeMinutes = 600,
            userRating = 5,
            userDifficulty = 3,
            completion = 100,
            isFavorite = true,
            isHidden = false,
            addedAt = addedTime,
            lastPlayed = disc1Played,
            achievementCount = 50
        )

        val disc2 = createGameEntity(
            id = 2,
            title = "Final Fantasy VII (Disc 2)",
            rommId = 101,
            igdbId = 427,
            platformId = 10,
            localPath = null,
            playCount = 8,
            playTimeMinutes = 400,
            userRating = 4,
            userDifficulty = 2,
            completion = 0,
            isFavorite = false,
            isHidden = false,
            addedAt = addedTime.plusSeconds(3600),
            lastPlayed = disc2Played,
            achievementCount = 30
        )

        val disc3 = createGameEntity(
            id = 3,
            title = "Final Fantasy VII (Disc 3)",
            rommId = 102,
            igdbId = 427,
            platformId = 10,
            localPath = null,
            playCount = 5,
            playTimeMinutes = 200,
            userRating = 0,
            userDifficulty = 0,
            completion = 0,
            isFavorite = false,
            isHidden = false,
            addedAt = addedTime.plusSeconds(7200),
            lastPlayed = disc3Played,
            achievementCount = 20
        )

        val result = GameMigrationHelper.aggregateMultiDiscData(
            listOf(disc1, disc2, disc3),
            pathValidator = { true }
        )!!

        assertEquals(0L, result.id)
        assertEquals("/storage/psx/ff7_disc1.bin", result.localPath)
        assertEquals(28, result.playCount)
        assertEquals(1200, result.playTimeMinutes)
        assertEquals(5, result.userRating)
        assertEquals(3, result.userDifficulty)
        assertEquals(100, result.completion)
        assertTrue(result.isFavorite)
        assertFalse(result.isHidden)
        assertEquals(addedTime, result.addedAt)
        assertEquals(disc2Played, result.lastPlayed)
        assertEquals(50, result.achievementCount)
    }
}
