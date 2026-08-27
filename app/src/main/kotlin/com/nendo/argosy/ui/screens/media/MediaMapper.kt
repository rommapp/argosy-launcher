package com.nendo.argosy.ui.screens.media

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaCreditEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.repository.MediaImageType
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionFact

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
 *
 * [verified] is what a verification pass established about downloaded copies, and [gradients] the
 * colours sampled from posters already drawn. Both are passed in as whole maps rather than looked up
 * per item so that building a screenful of tiles stays a set of map reads; nothing here touches the
 * filesystem. An item with nothing sampled yet draws against the theme accent until its poster
 * loads.
 */
fun MediaItemEntity.toMediaItemUi(
    repository: MediaRepository,
    userData: MediaUserDataEntity?,
    verified: Map<String, MediaAvailability> = emptyMap(),
    gradients: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>> = emptyMap()
): MediaItemUi {
    val position = userData?.playbackPositionTicks ?: 0
    val played = userData?.played ?: false
    return MediaItemUi(
        itemId = itemId,
        libraryId = libraryId,
        title = name,
        posterUrl = repository.posterUrl(itemId, primaryImageTag),
        backdropUrl = heroImageUrl(repository),
        thumbUrl = wideImageUrl(repository),
        overview = overview?.let(::plainText)?.takeIf { it.isNotBlank() },
        year = productionYear,
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
        availability = mediaAvailabilityOf(localPath, verified[itemId]),
        resumeTicks = position,
        runTimeTicks = runTimeTicks,
        played = played,
        isFavorite = userData?.isFavorite ?: false,
        progressFraction = progressFraction(position, runTimeTicks, played),
        gradientColors = gradients[itemId]
    )
}

/**
 * The landscape image a row draws, resolved as a kind and an item together.
 *
 * An episode's own artwork is its Primary image -- the still for that episode -- so that is what an
 * episode is asked for first, and it is what makes one row in a season distinguishable from the next.
 * The series' Thumb stands in only when the episode has no image at all, because it is the same
 * picture for every episode of the show and reads as artwork missing rather than artwork found. A
 * movie or a series asks for its own Thumb and falls back to its own poster.
 *
 * The kind travels with the tag deliberately. A tag belongs to one image of one item, so falling
 * back to a different tag while still asking for Thumb is a request the server answers 404.
 */
private fun MediaItemEntity.wideImageUrl(repository: MediaRepository): String {
    if (MediaItemType.fromWire(itemType) == MediaItemType.EPISODE) {
        primaryImageTag?.let { return repository.imageUrl(itemId, MediaImageType.PRIMARY, it) }
        seriesId?.let { return repository.imageUrl(it, MediaImageType.THUMB, null) }
        return ""
    }
    thumbImageTag?.let { return repository.imageUrl(itemId, MediaImageType.THUMB, it) }
    primaryImageTag?.let { return repository.imageUrl(itemId, MediaImageType.PRIMARY, it) }
    return ""
}

/**
 * How this title reads on the screen that is describing rather than driving.
 *
 * Facts are chosen for legibility from a distance rather than completeness: a runtime and a year
 * answer "is this the one I meant", where a genre list read across a room answers nothing.
 */
fun MediaItemUi.toCompanionDetail(context: Context): CompanionDetail =
    CompanionDetail(
        title = title,
        subtitle = listOfNotNull(seriesName, episodeLabel(context)).joinToString(" - ").takeIf { it.isNotBlank() },
        overview = overview,
        artUrl = posterUrl.takeIf { it.isNotBlank() },
        backdropUrl = backdropUrl.takeIf { it.isNotBlank() },
        facts = buildList {
            year?.let {
                add(CompanionFact(context.getString(R.string.media_companion_fact_year), it.toString()))
            }
            runtimeLabel(context)?.let {
                add(CompanionFact(context.getString(R.string.media_companion_fact_runtime), it))
            }
            officialRating?.let {
                add(CompanionFact(context.getString(R.string.media_companion_fact_rated), it))
            }
            communityRating?.let {
                add(CompanionFact(context.getString(R.string.media_companion_fact_rating), "%.1f".format(it)))
            }
        }
    )

/**
 * The season/episode tag a season or episode row leads with, e.g. "S1 E4". Absent for a movie,
 * which carries neither number.
 */
fun MediaItemUi.episodeLabel(context: Context): String? {
    val season = seasonNumber ?: return null
    val episode = episodeNumber ?: return null
    return context.getString(R.string.media_item_episode_label, season, episode)
}

fun MediaItemUi.runtimeLabel(context: Context): String? =
    runTimeTicks?.let { formatRuntime(context, it) }

/**
 * The full-bleed image a screen draws behind its content, resolved as a kind and an item together.
 *
 * An episode asks its series rather than itself. Episodes seldom carry a backdrop of their own, and
 * the series' art is the picture a screen showing an episode wants anyway.
 *
 * Everything else walks its own Backdrop, then its series' Backdrop where it has one, then its own
 * Thumb, and only then its poster. Backdrop and Thumb are landscape and fill a full-bleed frame at
 * the shape it wants; a poster is portrait and has to be blown up past its own width to cover the
 * same area, which is what makes a background look soft. It stays last rather than being dropped
 * because it is the one image every item has.
 *
 * No size is requested anywhere in the chain, so the server answers with the original rather than a
 * resized copy: the ceiling is what the library holds, not what is asked for.
 *
 * The kind travels with the tag for the reason [wideImageUrl] pairs them: a tag names one image of
 * one item, so a parent's tag against a child's id is a request the server answers 404. A series is
 * therefore asked for untagged rather than asked for with a tag that is not its own.
 */
private fun MediaItemEntity.heroImageUrl(repository: MediaRepository): String {
    if (MediaItemType.fromWire(itemType) == MediaItemType.EPISODE) {
        seriesId?.let { return repository.imageUrl(it, MediaImageType.BACKDROP, null) }
    }
    backdropImageTag?.let { return repository.imageUrl(itemId, MediaImageType.BACKDROP, it) }
    seriesId?.let { return repository.imageUrl(it, MediaImageType.BACKDROP, null) }
    thumbImageTag?.let { return repository.imageUrl(itemId, MediaImageType.THUMB, it) }
    primaryImageTag?.let { return repository.imageUrl(itemId, MediaImageType.PRIMARY, it) }
    return ""
}

/**
 * Overviews arrive as HTML. Break tags become line breaks, every other tag is dropped, and the
 * handful of entities a synopsis actually carries are decoded, so a description reads as prose
 * rather than as markup.
 */
private fun plainText(html: String): String = html
    .replace(Regex("(?i)<br\\s*/?>"), "\n")
    .replace(Regex("(?i)</p>"), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()

private fun progressFraction(positionTicks: Long, runTimeTicks: Long?, played: Boolean): Float {
    if (played) return 1f
    if (positionTicks <= 0 || runTimeTicks == null || runTimeTicks <= 0) return 0f
    return (positionTicks.toFloat() / runTimeTicks.toFloat()).coerceIn(0f, FINISHED_FRACTION)
}

fun formatRuntime(context: Context, ticks: Long): String {
    val totalMinutes = ticks / TICKS_PER_SECOND / SECONDS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours > 0 && minutes > 0 ->
            context.getString(R.string.media_item_runtime_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.media_item_runtime_hours, hours)
        else -> context.getString(R.string.media_item_runtime_minutes, minutes)
    }
}

/**
 * A credit as the cast rail draws it.
 *
 * The portrait is addressed against the PERSON's id, not the title's: a portrait belongs to the
 * person and is the same image wherever they are credited.
 */
fun MediaCreditEntity.toCastUi(repository: MediaRepository): MediaCastUi = MediaCastUi(
    personId = personId,
    name = name,
    role = role,
    imageUrl = primaryImageTag
        ?.let { repository.imageUrl(personId, MediaImageType.PRIMARY, it) }
        .orEmpty()
)

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
