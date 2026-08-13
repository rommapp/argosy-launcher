package com.nendo.argosy.ui.screens.media

import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.repository.MediaRepository

private const val TICKS_PER_SECOND = MediaRepository.TICKS_PER_SECOND
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val FINISHED_FRACTION = 0.95f

fun MediaLibraryEntity.toMediaLibraryUi(): MediaLibraryUi = MediaLibraryUi(
    libraryId = libraryId,
    name = name,
    isSeriesLibrary = MediaCollectionType.fromWire(collectionType) == MediaCollectionType.TV_SHOWS,
    itemCount = itemCount
)

fun MediaItemEntity.toMediaSeasonUi(): MediaSeasonUi = MediaSeasonUi(
    itemId = itemId,
    name = name,
    seasonNumber = indexNumber,
    episodeCount = childCount
)

/**
 * Builds the drawable form of an item. Image addresses are asked of the repository rather than
 * assembled here: they carry the server's own image tag, which is what makes them cacheable
 * forever, and a hand-built address that drops the tag silently loses that.
 */
fun MediaItemEntity.toMediaItemUi(
    repository: MediaRepository,
    userData: MediaUserDataEntity?
): MediaItemUi {
    val position = userData?.playbackPositionTicks ?: 0
    val played = userData?.played ?: false
    return MediaItemUi(
        itemId = itemId,
        title = name,
        posterUrl = repository.posterUrl(itemId, primaryImageTag),
        backdropUrl = repository.backdropUrl(itemId, backdropImageTag),
        thumbUrl = repository.thumbUrl(itemId, thumbImageTag ?: primaryImageTag),
        overview = overview?.takeIf { it.isNotBlank() },
        year = productionYear,
        runtimeLabel = runTimeTicks?.let { formatRuntime(it) },
        communityRating = communityRating,
        officialRating = officialRating?.takeIf { it.isNotBlank() },
        genres = genres?.takeIf { it.isNotBlank() }?.replace(",", ", "),
        studios = studios?.takeIf { it.isNotBlank() }?.replace(",", ", "),
        seriesId = seriesId,
        seriesName = seriesName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        childCount = childCount,
        isSeries = MediaItemType.fromWire(itemType) == MediaItemType.SERIES,
        isDownloaded = localPath != null,
        resumeTicks = position,
        runTimeTicks = runTimeTicks,
        played = played,
        isFavorite = userData?.isFavorite ?: false,
        progressFraction = progressFraction(position, runTimeTicks, played)
    )
}

private fun progressFraction(positionTicks: Long, runTimeTicks: Long?, played: Boolean): Float {
    if (played) return 1f
    if (positionTicks <= 0 || runTimeTicks == null || runTimeTicks <= 0) return 0f
    return (positionTicks.toFloat() / runTimeTicks.toFloat()).coerceIn(0f, FINISHED_FRACTION)
}

fun formatRuntime(ticks: Long): String {
    val totalMinutes = ticks / TICKS_PER_SECOND / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/**
 * A position as a clock reading. The hour field is dropped below an hour so a short episode does not
 * read as "0:04:12".
 */
fun formatPosition(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
