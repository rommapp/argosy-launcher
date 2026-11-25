package com.nendo.argosy.data.music.stats

import com.nendo.argosy.data.local.dao.MusicContextListenRow
import com.nendo.argosy.data.local.dao.MusicFlashbackRow
import com.nendo.argosy.data.local.dao.MusicPairingRow
import com.nendo.argosy.data.local.dao.MusicTrackStatRow
import com.nendo.argosy.domain.model.MusicContextTagKind
import com.nendo.argosy.domain.model.MusicFlashbackYear
import com.nendo.argosy.domain.model.MusicPairingGroup
import com.nendo.argosy.domain.model.MusicRecommendation
import com.nendo.argosy.domain.model.MusicStatTrack
import java.util.concurrent.TimeUnit
import kotlin.math.pow

internal object MusicStatsAggregation {

    fun toStat(row: MusicTrackStatRow): MusicStatTrack = MusicStatTrack(
        groupKey = row.groupKey,
        trackKey = row.trackKey,
        recordingKey = row.recordingKey,
        source = row.source,
        sourceAccountKey = row.sourceAccountKey,
        sourceTrackId = row.sourceTrackId,
        albumKey = row.albumKey,
        romId = row.romId,
        romFileId = row.romFileId,
        title = row.title,
        artistCredit = row.artistCredit,
        albumTitle = row.albumTitle,
        trackGameTitle = row.trackGameTitle,
        durationMs = row.durationMs,
        listenedMs = row.totalMs,
        playCount = row.playCount,
        lastListenedAt = row.lastStartedAt
    )

    fun flashbackYears(rows: List<MusicFlashbackRow>, perYear: Int): List<MusicFlashbackYear> =
        rows.groupBy { it.year }
            .map { (year, members) ->
                MusicFlashbackYear(
                    year = year,
                    listenedMs = members.sumOf { it.track.totalMs },
                    playCount = members.sumOf { it.track.playCount },
                    tracks = members.map { toStat(it.track) }.sortedWith(BY_LISTENED).take(perYear)
                )
            }
            .sortedByDescending { it.year }

    fun pairingGroups(rows: List<MusicPairingRow>, perGroup: Int, limit: Int): List<MusicPairingGroup> =
        rows.groupBy { it.contextKey }
            .map { (key, members) ->
                val tracks = members.map { toStat(it.track) }.sortedWith(BY_LISTENED)
                MusicPairingGroup(
                    groupKey = key,
                    label = members.maxBy { it.track.lastStartedAt }.contextLabel,
                    listenedMs = tracks.sumOf { it.listenedMs },
                    tracks = tracks.take(perGroup)
                )
            }
            .sortedWith(compareByDescending<MusicPairingGroup> { it.listenedMs }.thenBy { it.groupKey })
            .take(limit)

    private val BY_LISTENED = compareByDescending<MusicStatTrack> { it.listenedMs }
        .thenByDescending { it.lastListenedAt }
        .thenBy { it.groupKey }
}

internal object MusicRecommendationScoring {
    const val SAME_GAME_WEIGHT = 1.0
    const val FRANCHISE_WEIGHT = 0.6
    const val COLLECTION_WEIGHT = 0.3
    const val GENRE_WEIGHT = 0.2
    const val PLATFORM_WEIGHT = 0.1
    const val QUICK_SKIP_MS = 30_000L
    const val QUICK_SKIP_PENALTY_MS = 60_000.0
    val HALF_LIFE_MS: Long = TimeUnit.DAYS.toMillis(90)

    data class Target(
        val gameId: Long,
        val gameKey: String,
        val platformSlug: String,
        val tags: Set<Pair<MusicContextTagKind, String>>,
        val seedAlbumKey: String?
    )

    fun weight(target: Target, row: MusicContextListenRow, contextTags: Set<Pair<MusicContextTagKind, String>>): Double {
        val sameGame = (row.contextGameId != null && row.contextGameId == target.gameId) ||
            (row.contextGameKey != null && row.contextGameKey == target.gameKey)
        val ownSoundtrack = target.seedAlbumKey != null && row.albumKey == target.seedAlbumKey
        if (sameGame || ownSoundtrack) return SAME_GAME_WEIGHT
        val shared = contextTags.intersect(target.tags).map { it.first }.toSet()
        return when {
            MusicContextTagKind.FRANCHISE in shared -> FRANCHISE_WEIGHT
            MusicContextTagKind.COLLECTION in shared -> COLLECTION_WEIGHT
            MusicContextTagKind.GENRE in shared -> GENRE_WEIGHT
            row.contextPlatformSlug != null && row.contextPlatformSlug == target.platformSlug -> PLATFORM_WEIGHT
            else -> 0.0
        }
    }

    fun decay(ageMs: Long): Double = 0.5.pow(ageMs.coerceAtLeast(0L).toDouble() / HALF_LIFE_MS)

    fun isQuickSkip(row: MusicContextListenRow): Boolean =
        row.listenedMs < QUICK_SKIP_MS && row.endReason == MusicListenEndReason.SKIPPED.token

    fun contribution(row: MusicContextListenRow, weight: Double, nowMs: Long): Double {
        val factor = weight * decay(nowMs - row.startedAt)
        return if (isQuickSkip(row)) -factor * QUICK_SKIP_PENALTY_MS else factor * row.listenedMs
    }

    fun rank(
        target: Target,
        rows: List<MusicContextListenRow>,
        tagsByContext: Map<Long, Set<Pair<MusicContextTagKind, String>>>,
        seeds: List<MusicStatTrack>,
        nowMs: Long,
        limit: Int
    ): List<MusicRecommendation> {
        val weighted = rows.mapNotNull { row ->
            val tags = row.contextId?.let { tagsByContext[it] }.orEmpty()
            val weight = weight(target, row, tags)
            if (weight > 0.0) row to weight else null
        }
        val scored = weighted.groupBy { (row, _) -> row.recordingKey ?: row.trackKey }
            .mapNotNull { (groupKey, members) ->
                val score = members.sumOf { (row, weight) -> contribution(row, weight, nowMs) }
                if (score <= 0.0) return@mapNotNull null
                val latest = members.maxBy { (row, _) -> row.startedAt }.first
                MusicRecommendation(
                    track = statOf(groupKey, latest, members.map { it.first }),
                    score = score,
                    seeded = false
                )
            }
            .sortedWith(
                compareByDescending<MusicRecommendation> { it.score }
                    .thenByDescending { it.track.lastListenedAt }
                    .thenBy { it.track.groupKey }
            )
        val heard = rows.mapTo(HashSet()) { it.trackKey }
        val seeded = seeds.filter { it.trackKey !in heard }.distinctBy { it.trackKey }
            .map { MusicRecommendation(track = it, score = 0.0, seeded = true) }
        return (scored + seeded).take(limit)
    }

    private fun statOf(groupKey: String, latest: MusicContextListenRow, members: List<MusicContextListenRow>) =
        MusicStatTrack(
            groupKey = groupKey,
            trackKey = latest.trackKey,
            recordingKey = latest.recordingKey,
            source = latest.source,
            sourceAccountKey = latest.sourceAccountKey,
            sourceTrackId = latest.sourceTrackId,
            albumKey = latest.albumKey,
            romId = latest.romId,
            romFileId = latest.romFileId,
            title = latest.title,
            artistCredit = latest.artistCredit,
            albumTitle = latest.albumTitle,
            trackGameTitle = latest.trackGameTitle,
            durationMs = latest.durationMs,
            listenedMs = members.sumOf { it.listenedMs },
            playCount = members.count { it.counted },
            lastListenedAt = latest.startedAt
        )
}
