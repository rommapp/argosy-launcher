package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.repository.MediaRepository

private const val FINISHED_FRACTION = 0.95f

/**
 * One stored media item as a home tile, shared by every surface that draws a media rail.
 *
 * An episode is drawn as its show -- the show's poster, the show's name -- while the tile still
 * plays the episode. The show's own artwork is preferred when its row has been synced and falls
 * back to the untagged address for the same show, which the server still answers; only when there
 * is no show at all does the tile fall back to the episode's own still.
 */
fun MediaItemEntity.toHomeMediaUi(
    repository: MediaRepository,
    userData: MediaUserDataEntity? = null,
    series: MediaItemEntity? = null,
    verified: MediaAvailability? = null,
    gradientColors: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>? = null
): HomeMediaUi {
    val position = userData?.playbackPositionTicks ?: 0
    val played = userData?.played ?: false
    val kind = MediaItemType.fromWire(itemType)
    val isEpisode = kind == MediaItemType.EPISODE
    val posterId = if (isEpisode) seriesId ?: itemId else itemId
    val posterTag = when {
        !isEpisode -> primaryImageTag
        series != null -> series.primaryImageTag
        seriesId != null -> null
        else -> primaryImageTag
    }
    return HomeMediaUi(
        itemId = itemId,
        title = if (isEpisode) seriesName ?: series?.name ?: name else name,
        subtitle = if (isEpisode) episodeSubtitle() else productionYear?.toString(),
        posterUrl = repository.posterUrl(posterId, posterTag),
        seriesId = seriesId,
        isEpisode = isEpisode,
        isSeries = kind == MediaItemType.SERIES,
        availability = mediaAvailabilityOf(localPath, verified),
        resumeTicks = position,
        progressFraction = mediaProgressFraction(position, runTimeTicks, played),
        gradientColors = gradientColors
    )
}

/**
 * The episode a tile will actually play, spelled out. Numbering is dropped when the server did not
 * give it rather than printed as a blank, because a special has a name and no numbers.
 */
private fun MediaItemEntity.episodeSubtitle(): String {
    val season = parentIndexNumber
    val episode = indexNumber
    val marker = when {
        season != null && episode != null -> "S$season E$episode"
        episode != null -> "E$episode"
        else -> null
    }
    return listOfNotNull(marker, name).joinToString(" - ")
}

private fun mediaProgressFraction(positionTicks: Long, runTimeTicks: Long?, played: Boolean): Float {
    if (played) return 1f
    if (positionTicks <= 0 || runTimeTicks == null || runTimeTicks <= 0) return 0f
    return (positionTicks.toFloat() / runTimeTicks.toFloat()).coerceIn(0f, FINISHED_FRACTION)
}
