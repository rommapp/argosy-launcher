package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.data.music.stats.MusicStatsRepository
import com.nendo.argosy.domain.model.DigestMusic
import com.nendo.argosy.domain.music.MusicStatsCalendar
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth
import javax.inject.Inject

/**
 * The listening part of the monthly recap for [YearMonth], or null when the active account heard
 * nothing that month. The top album's cover is looked up for at most [ART_TIMEOUT_MS].
 */
class GetMonthlyMusicDigestUseCase @Inject constructor(
    private val stats: MusicStatsRepository,
    private val library: MusicLibrary
) {
    suspend operator fun invoke(month: YearMonth): DigestMusic? {
        val yearMonth = MusicStatsCalendar.yearMonthOf(month)
        val totals = stats.monthTotals(yearMonth)
        if (totals.listenedMs <= 0L) return null
        val days = MusicStatsCalendar.dayRange(yearMonth)
        val topAlbum = stats.topAlbums(days.first, days.last, 1).firstOrNull()
        val art = topAlbum?.let { album ->
            withTimeoutOrNull(ART_TIMEOUT_MS) { library.albumsForKeys(listOf(album.albumKey))[album.albumKey]?.art }
        }
        return DigestMusic(
            listenedMs = totals.listenedMs,
            playCount = totals.playCount,
            topTrack = stats.topTracks(days.first, days.last, 1).firstOrNull(),
            topAlbum = topAlbum,
            topAlbumArt = art,
            topArtist = stats.topArtists(days.first, days.last, 1).firstOrNull()
        )
    }

    private companion object {
        const val ART_TIMEOUT_MS = 3_000L
    }
}
