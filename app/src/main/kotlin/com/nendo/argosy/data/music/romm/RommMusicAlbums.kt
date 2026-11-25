package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import com.nendo.argosy.domain.music.CreditSplitter

internal data class RommAlbumSummary(
    val romId: Long,
    val title: String,
    val artist: String?,
    val credits: List<String>,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationSec: Int,
    val coverUrl: String?
)

internal data class RommArtistSummary(
    val name: String,
    val albumCount: Int
)

internal object RommMusicAlbums {

    fun albums(rows: List<RommMusicTrackEntity>): List<RommAlbumSummary> =
        rows.groupBy { it.romId }.map { (romId, tracks) -> summarize(romId, tracks) }

    fun artists(rows: List<RommMusicTrackEntity>): List<RommArtistSummary> =
        rows.flatMap { row -> CreditSplitter.split(row.artist).map { it to row.romId } }
            .groupBy({ it.first }, { it.second })
            .map { (name, romIds) -> RommArtistSummary(name, romIds.distinct().size) }
            .sortedBy { it.name.lowercase() }

    private fun summarize(romId: Long, tracks: List<RommMusicTrackEntity>) = RommAlbumSummary(
        romId = romId,
        title = mostCommon(tracks.mapNotNull { it.album.tagOrNull() })
            ?: tracks.firstNotNullOfOrNull { it.gameName.tagOrNull() }
            ?: romId.toString(),
        artist = mostCommon(tracks.mapNotNull { it.artist.tagOrNull() }),
        credits = tracks.flatMap { CreditSplitter.split(it.artist) }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key },
        year = mostCommon(tracks.mapNotNull { track -> track.year?.takeIf { it > 0 } }),
        genre = mostCommon(tracks.mapNotNull { it.genre.tagOrNull() }),
        trackCount = tracks.size,
        durationSec = tracks.sumOf { it.durationSeconds ?: 0.0 }.toInt(),
        coverUrl = tracks.firstNotNullOfOrNull { it.gameCoverUrl.tagOrNull() }
            ?: tracks.firstNotNullOfOrNull { it.coverUrl.tagOrNull() }
    )

    private fun <T : Comparable<T>> mostCommon(values: List<T>): T? =
        values.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<T, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

    private fun String?.tagOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
