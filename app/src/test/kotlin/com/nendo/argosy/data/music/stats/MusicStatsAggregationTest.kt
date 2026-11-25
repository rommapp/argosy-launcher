package com.nendo.argosy.data.music.stats

import com.nendo.argosy.data.local.dao.MusicContextListenRow
import com.nendo.argosy.data.local.dao.MusicFlashbackRow
import com.nendo.argosy.data.local.dao.MusicPairingRow
import com.nendo.argosy.data.local.dao.MusicTrackStatRow
import com.nendo.argosy.domain.model.MusicContextTagKind
import com.nendo.argosy.domain.model.MusicStatTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MusicStatsAggregationTest {

    private fun stat(key: String, totalMs: Long, last: Long = 0, plays: Int = 1) = MusicTrackStatRow(
        groupKey = key,
        trackKey = key,
        recordingKey = null,
        source = "navidrome",
        sourceAccountKey = "alice@nd",
        sourceTrackId = key,
        albumKey = null,
        romId = null,
        romFileId = null,
        title = key,
        artistCredit = null,
        albumTitle = null,
        trackGameTitle = null,
        durationMs = 200_000,
        totalMs = totalMs,
        playCount = plays,
        lastStartedAt = last
    )

    @Test
    fun `flashback years sum every track, keep the top per year and lead with the newest year`() {
        val rows = listOf(
            MusicFlashbackRow(2024, stat("a", 10_000)),
            MusicFlashbackRow(2025, stat("b", 5_000)),
            MusicFlashbackRow(2024, stat("c", 30_000)),
            MusicFlashbackRow(2024, stat("d", 20_000))
        )

        val years = MusicStatsAggregation.flashbackYears(rows, perYear = 2)

        assertEquals(listOf(2025, 2024), years.map { it.year })
        assertEquals(60_000L, years[1].listenedMs)
        assertEquals(3, years[1].playCount)
        assertEquals(listOf("c", "d"), years[1].tracks.map { it.groupKey })
    }

    @Test
    fun `pairing groups rank by total time and take the label from the latest listen`() {
        val rows = listOf(
            MusicPairingRow("igdb:1", "Old Title", stat("a", 10_000, last = 1)),
            MusicPairingRow("igdb:1", "New Title", stat("b", 15_000, last = 9)),
            MusicPairingRow("igdb:2", "Other", stat("c", 40_000, last = 5))
        )

        val groups = MusicStatsAggregation.pairingGroups(rows, perGroup = 1, limit = 10)

        assertEquals(listOf("igdb:2", "igdb:1"), groups.map { it.groupKey })
        assertEquals("New Title", groups[1].label)
        assertEquals(25_000L, groups[1].listenedMs)
        assertEquals(listOf("b"), groups[1].tracks.map { it.groupKey })
    }

    @Test
    fun `equal listening time breaks ties by recency then key`() {
        val rows = listOf(
            MusicFlashbackRow(2024, stat("z", 10_000, last = 1)),
            MusicFlashbackRow(2024, stat("y", 10_000, last = 2)),
            MusicFlashbackRow(2024, stat("x", 10_000, last = 2))
        )

        assertEquals(listOf("x", "y", "z"), MusicStatsAggregation.flashbackYears(rows, 3).single().tracks.map { it.groupKey })
    }
}

class MusicRecommendationScoringTest {

    private val now = TimeUnit.DAYS.toMillis(1_000)
    private val halfLife = MusicRecommendationScoring.HALF_LIFE_MS

    private val target = MusicRecommendationScoring.Target(
        gameId = 1L,
        gameKey = "igdb:100",
        platformSlug = "switch",
        tags = setOf(
            MusicContextTagKind.FRANCHISE to "zelda",
            MusicContextTagKind.COLLECTION to "open world",
            MusicContextTagKind.GENRE to "adventure"
        ),
        seedAlbumKey = "romm:album:r55"
    )

    private fun row(
        trackKey: String,
        listenedMs: Long = 100_000,
        contextId: Long? = 10,
        gameId: Long? = 2,
        gameKey: String? = "igdb:200",
        platform: String? = "snes",
        albumKey: String? = null,
        startedAt: Long = now,
        endReason: String? = "completed",
        recordingKey: String? = null
    ) = MusicContextListenRow(
        listenId = 0,
        contextId = contextId,
        contextGameId = gameId,
        contextGameKey = gameKey,
        contextPlatformSlug = platform,
        trackKey = trackKey,
        recordingKey = recordingKey,
        source = "romm",
        sourceAccountKey = "7@romm",
        sourceTrackId = trackKey,
        albumKey = albumKey,
        romId = null,
        romFileId = null,
        title = trackKey,
        artistCredit = null,
        albumTitle = null,
        trackGameTitle = null,
        durationMs = 200_000,
        listenedMs = listenedMs,
        counted = listenedMs >= 100_000,
        startedAt = startedAt,
        endReason = endReason
    )

    private fun weight(row: MusicContextListenRow, tags: Set<Pair<MusicContextTagKind, String>> = emptySet()) =
        MusicRecommendationScoring.weight(target, row, tags)

    @Test
    fun `same game by id or key and the own soundtrack weigh fully`() {
        assertEquals(1.0, weight(row("a", gameId = 1, gameKey = "igdb:999")), 0.0)
        assertEquals(1.0, weight(row("a", gameId = 3, gameKey = "igdb:100")), 0.0)
        assertEquals(1.0, weight(row("a", contextId = null, gameId = null, gameKey = null, platform = null, albumKey = "romm:album:r55")), 0.0)
    }

    @Test
    fun `the strongest shared tag wins over weaker ones and the platform`() {
        val all = setOf(
            MusicContextTagKind.GENRE to "adventure",
            MusicContextTagKind.COLLECTION to "open world",
            MusicContextTagKind.FRANCHISE to "zelda"
        )
        assertEquals(0.6, weight(row("a", platform = "switch"), all), 0.0)
        assertEquals(0.3, weight(row("a", platform = "switch"), setOf(MusicContextTagKind.COLLECTION to "open world")), 0.0)
        assertEquals(0.2, weight(row("a", platform = "switch"), setOf(MusicContextTagKind.GENRE to "adventure")), 0.0)
        assertEquals(0.1, weight(row("a", platform = "switch")), 0.0)
        assertEquals(0.0, weight(row("a")), 0.0)
    }

    @Test
    fun `a tag key shared under a different kind does not match`() {
        assertEquals(0.0, weight(row("a"), setOf(MusicContextTagKind.GENRE to "zelda")), 0.0)
    }

    @Test
    fun `decay halves every half life and never exceeds one`() {
        assertEquals(1.0, MusicRecommendationScoring.decay(0), 1e-9)
        assertEquals(0.5, MusicRecommendationScoring.decay(halfLife), 1e-9)
        assertEquals(0.25, MusicRecommendationScoring.decay(halfLife * 2), 1e-9)
        assertEquals(1.0, MusicRecommendationScoring.decay(-halfLife), 1e-9)
    }

    @Test
    fun `heard time ranks a track and an older listen weighs less than a newer one`() {
        val rows = listOf(
            row("old", gameId = 1, startedAt = now - halfLife),
            row("new", gameId = 1, startedAt = now)
        )

        val ranked = MusicRecommendationScoring.rank(target, rows, emptyMap(), emptyList(), now, 10)

        assertEquals(listOf("new", "old"), ranked.map { it.track.trackKey })
        assertEquals(100_000.0, ranked[0].score, 1e-6)
        assertEquals(50_000.0, ranked[1].score, 1e-6)
    }

    @Test
    fun `quick skips subtract and a track skipped more than heard drops out`() {
        val rows = listOf(
            row("kept", gameId = 1, listenedMs = 120_000),
            row("kept", gameId = 1, listenedMs = 5_000, endReason = "skipped"),
            row("dropped", gameId = 1, listenedMs = 40_000),
            row("dropped", gameId = 1, listenedMs = 2_000, endReason = "skipped")
        )

        val ranked = MusicRecommendationScoring.rank(target, rows, emptyMap(), emptyList(), now, 10)

        assertEquals(listOf("kept"), ranked.map { it.track.trackKey })
        assertEquals(60_000.0, ranked.single().score, 1e-6)
        assertEquals(1, ranked.single().track.playCount)
    }

    @Test
    fun `a short listen ended without a skip is not a quick skip`() {
        val stopped = row("a", gameId = 1, listenedMs = 10_000, endReason = "stopped")
        assertEquals(false, MusicRecommendationScoring.isQuickSkip(stopped))
        assertEquals(10_000.0, MusicRecommendationScoring.contribution(stopped, 1.0, now), 1e-6)
    }

    @Test
    fun `listens in unrelated contexts score nothing`() {
        val ranked = MusicRecommendationScoring.rank(target, listOf(row("a")), emptyMap(), emptyList(), now, 10)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `listens share a group by recording key across sources`() {
        val rows = listOf(
            row("navidrome:track:1", gameId = 1, recordingKey = "isrc:X"),
            row("romm:track:2", gameId = 1, recordingKey = "isrc:X", startedAt = now - 1)
        )

        val ranked = MusicRecommendationScoring.rank(target, rows, emptyMap(), emptyList(), now, 10)

        assertEquals("isrc:X", ranked.single().track.groupKey)
        assertEquals("navidrome:track:1", ranked.single().track.trackKey)
        assertEquals(200_000L, ranked.single().track.listenedMs)
    }

    @Test
    fun `tags come from the listen's context`() {
        val rows = listOf(row("a", contextId = 10), row("b", contextId = 11))
        val tags = mapOf(10L to setOf(MusicContextTagKind.FRANCHISE to "zelda"))

        val ranked = MusicRecommendationScoring.rank(target, rows, tags, emptyList(), now, 10)

        assertEquals(listOf("a"), ranked.map { it.track.trackKey })
        assertEquals(60_000.0, ranked.single().score, 1e-6)
    }

    @Test
    fun `unheard soundtrack seeds follow the scored tracks and heard ones are not seeded again`() {
        val rows = listOf(
            row("romm:track:1", contextId = null, gameId = null, gameKey = null, platform = null, albumKey = "romm:album:r55"),
            row("romm:track:2", gameId = 1, listenedMs = 3_000, endReason = "skipped")
        )
        val seeds = listOf(seed("romm:track:1"), seed("romm:track:2"), seed("romm:track:3"), seed("romm:track:4"))

        val ranked = MusicRecommendationScoring.rank(target, rows, emptyMap(), seeds, now, 3)

        assertEquals(listOf("romm:track:1", "romm:track:3", "romm:track:4"), ranked.map { it.track.trackKey })
        assertEquals(listOf(false, true, true), ranked.map { it.seeded })
    }

    private fun seed(trackKey: String) = MusicStatTrack(
        groupKey = trackKey,
        trackKey = trackKey,
        recordingKey = null,
        source = "romm",
        sourceAccountKey = "7@romm",
        sourceTrackId = trackKey,
        albumKey = "romm:album:r55",
        romId = 55,
        romFileId = null,
        title = trackKey,
        artistCredit = null,
        albumTitle = null,
        trackGameTitle = null,
        durationMs = 120_000,
        listenedMs = 0,
        playCount = 0,
        lastListenedAt = 0
    )
}
