package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.MediaTilePlayback
import com.nendo.argosy.domain.usecase.media.ResolveMediaTileUseCase
import com.nendo.argosy.ui.components.MediaTileOption
import com.nendo.argosy.ui.home.grid.MEDIA_TILE_SIZE_WARNING_THRESHOLD
import com.nendo.argosy.ui.home.grid.MediaTileCatalog
import com.nendo.argosy.ui.home.grid.MediaTileDownloadPlan
import javax.inject.Inject
import javax.inject.Singleton

private const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0

private const val SPECIALS_SEASON = 0

/**
 * The library half of the curated grid's tile picker.
 *
 * Every question the picker asks about a series is answered from what has already been synced, so a
 * grid can be arranged with no network. That also means a season with no stored episodes answers
 * with nothing, and the picker says so rather than offering a season that would place a tile with
 * nothing behind it.
 *
 * This is also where a tile's playback is resolved from, so the grid has one place to ask what a
 * tile stands for rather than reaching into the media stack itself.
 */
@Singleton
class HomeTilePickerDelegate @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaDownloadManager: MediaDownloadManager,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val resolveMediaTile: ResolveMediaTileUseCase
) : MediaTileCatalog {

    /**
     * What [tile] plays right now, including the case where it resolves to something real that is
     * not on this device yet. A tile only plays from local files, so that case is a state of its own
     * rather than a failure.
     */
    suspend fun playbackFor(tile: HomeTile): MediaTilePlayback = resolveMediaTile(tile)

    /**
     * Forgets a random tile's held choice once it has been played, so the next resolution picks
     * again rather than offering the episode that was just watched.
     */
    fun onTilePlayed(tileId: Long) = resolveMediaTile.releaseRandomChoice(tileId)

    override suspend fun seasons(seriesId: String): List<MediaTileOption> {
        val verified = availabilityVerifier.availability.value
        return mediaRepository.getSeasons(seriesId)
            .sortedBy { it.indexNumber ?: Int.MAX_VALUE }
            .map { season ->
                val episodes = mediaRepository.getEpisodes(season.itemId)
                val local = episodes.count {
                    mediaAvailabilityOf(it.localPath, verified[it.itemId]).hasLocalCopy
                }
                MediaTileOption(
                    itemId = season.itemId,
                    label = seasonLabel(season),
                    supporting = seasonSupporting(local, episodes.size),
                    isLocal = episodes.isNotEmpty() && local == episodes.size
                )
            }
    }

    override suspend fun episodes(seriesId: String, seasonId: String?): List<MediaTileOption> {
        val verified = availabilityVerifier.availability.value
        val rows = if (seasonId == null) {
            mediaRepository.getSeriesEpisodes(seriesId)
        } else {
            mediaRepository.getEpisodes(seasonId)
        }
        return rows.map { episode ->
            MediaTileOption(
                itemId = episode.itemId,
                label = episodeLabel(episode),
                supporting = episode.productionYear?.toString(),
                isLocal = mediaAvailabilityOf(
                    episode.localPath,
                    verified[episode.itemId]
                ).hasLocalCopy
            )
        }
    }

    /**
     * Which of the chosen titles are not on this device, and roughly what fetching them would take.
     *
     * A title the library has no row for counts as missing rather than as present: nothing here can
     * show it is on the device, and treating an unknown as downloaded is how a tile ends up pointing
     * at a file that was never fetched.
     *
     * The size is only worked out for a batch large enough to be worth stating, because reading it
     * costs one lookup per title and a batch of two does not need a figure at all.
     */
    override suspend fun planDownloads(itemIds: List<String>): MediaTileDownloadPlan {
        if (itemIds.isEmpty()) return MediaTileDownloadPlan()
        val verified = availabilityVerifier.availability.value
        val known = mediaRepository.getItems(itemIds).associateBy { it.itemId }
        val missing = itemIds.filterNot { itemId ->
            val item = known[itemId] ?: return@filterNot false
            mediaAvailabilityOf(item.localPath, verified[itemId]).hasLocalCopy
        }
        if (missing.size <= MEDIA_TILE_SIZE_WARNING_THRESHOLD) {
            return MediaTileDownloadPlan(missingIds = missing)
        }
        val quality = mediaDownloadManager.defaultQuality()
        val estimate = mediaDownloadManager.estimateBatch(missing)[quality]
        return MediaTileDownloadPlan(
            missingIds = missing,
            approximateSize = estimate?.bytes?.let { formatGigabytes(it) }
        )
    }

    /**
     * The quality is read once, here, and handed to the whole batch. Reading it per title would let
     * a preference changed mid-enqueue split one tile's episodes across two sizes.
     */
    override suspend fun enqueue(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        mediaDownloadManager.enqueueAll(itemIds, mediaDownloadManager.defaultQuality())
    }

    private fun seasonLabel(season: MediaItemEntity): String = when (season.indexNumber) {
        null -> season.name
        SPECIALS_SEASON -> "Specials"
        else -> "Season ${season.indexNumber}"
    }

    private fun seasonSupporting(local: Int, total: Int): String? = when {
        total == 0 -> "Nothing synced yet"
        local == total -> "All $total on this device"
        else -> "$local of $total on this device"
    }

    private fun episodeLabel(episode: MediaItemEntity): String {
        val season = episode.parentIndexNumber
        val number = episode.indexNumber
        val marker = when {
            season != null && number != null -> "S$season E$number"
            number != null -> "E$number"
            else -> null
        }
        return listOfNotNull(marker, episode.name).joinToString(" - ")
    }

    private fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
}
