package com.nendo.argosy.domain.usecase.media

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.MediaTilePendingReason
import com.nendo.argosy.domain.model.MediaTilePlayback
import com.nendo.argosy.domain.model.isPastMediaCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a curated media tile plays right now.
 *
 * SEQUENTIAL derives its position from watch state rather than a stored pointer. RANDOM holds its
 * choice in memory until it is watched. Both run only over episodes present on this device and wrap
 * to the first.
 */
@Singleton
class ResolveMediaTileUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val fileAccessLayer: FileAccessLayer,
    private val resolveMediaPlayTarget: ResolveMediaPlayTargetUseCase
) {
    private val randomChoices = ConcurrentHashMap<Long, String>()

    suspend operator fun invoke(tile: HomeTile): MediaTilePlayback =
        when (val target = tile.target) {
            is HomeTileTargetRef.LocalMedia -> resolveLocalFile(target.filePath)
            is HomeTileTargetRef.Media -> resolveLibrary(tile, target)
            else -> MediaTilePlayback.Unresolved
        }

    /**
     * Forgets the episode a random tile settled on, so the next resolution picks again. Called when
     * the tile has just played what it chose, which is the one moment the choice stops being current.
     */
    fun releaseRandomChoice(tileId: Long) {
        randomChoices.remove(tileId)
    }

    private suspend fun resolveLocalFile(path: String): MediaTilePlayback {
        if (path.isBlank()) return MediaTilePlayback.Unresolved
        val present = withContext(Dispatchers.IO) { fileAccessLayer.exists(path) }
        if (!present) return MediaTilePlayback.Unresolved
        return MediaTilePlayback.Ready(
            localPath = path,
            itemId = null,
            title = File(path).nameWithoutExtension,
            subtitle = null
        )
    }

    private suspend fun resolveLibrary(
        tile: HomeTile,
        target: HomeTileTargetRef.Media
    ): MediaTilePlayback {
        val itemId = chooseItem(tile, target) ?: return MediaTilePlayback.Unresolved
        val item = mediaRepository.getItem(itemId) ?: return MediaTilePlayback.Unresolved
        val availability = mediaAvailabilityOf(
            item.localPath,
            availabilityVerifier.availability.value[itemId]
        )
        val path = item.localPath
        if (path == null || !availability.playsFromDisk) {
            return MediaTilePlayback.Pending(
                itemId = itemId,
                title = titleOf(item),
                subtitle = subtitleOf(item),
                reason = if (availability == MediaAvailability.UNAVAILABLE) {
                    MediaTilePendingReason.STORAGE_UNAVAILABLE
                } else {
                    MediaTilePendingReason.NOT_DOWNLOADED
                }
            )
        }
        return MediaTilePlayback.Ready(
            localPath = path,
            itemId = itemId,
            title = titleOf(item),
            subtitle = subtitleOf(item),
            resumeTicks = resumeTicksOf(item)
        )
    }

    /**
     * The position a tile play opens at, taken straight to the player without the player's own
     * resume resolution. A position past the completion threshold is dropped here for the same
     * reason the player drops one: it is end-of-credits residue, and honouring it would open the
     * item on its final seconds.
     */
    private suspend fun resumeTicksOf(item: MediaItemEntity): Long {
        val userData = mediaRepository.getUserData(item.itemId) ?: return 0
        val ticks = userData.playbackPositionTicks
        if (ticks <= 0) return 0
        val finished = isPastMediaCompletion(
            positionMs = ticks / TICKS_PER_MILLISECOND,
            runtimeMs = (item.runTimeTicks ?: 0) / TICKS_PER_MILLISECOND,
            playedPercentage = userData.playedPercentage
        )
        return if (finished) 0 else ticks
    }

    private suspend fun chooseItem(tile: HomeTile, target: HomeTileTargetRef.Media): String? =
        when (target.playMode) {
            MediaTilePlayMode.SINGLE -> target.itemId
            MediaTilePlayMode.PLAYLIST -> nextOf(tile.playlist)
            MediaTilePlayMode.SEASON -> {
                val season = target.scopeId
                if (season == null) {
                    null
                } else {
                    nextOf(mediaRepository.getEpisodes(season).map { it.itemId })
                }
            }
            MediaTilePlayMode.SEQUENTIAL -> nextOf(localEpisodesOf(target.itemId))
            MediaTilePlayMode.RANDOM -> randomOf(tile.id, localEpisodesOf(target.itemId))
        }

    /**
     * Episodes of a series present on this device, in broadcast order.
     */
    private suspend fun localEpisodesOf(seriesId: String): List<String> =
        mediaRepository.getSeriesEpisodes(seriesId)
            .filter { it.localPath != null }
            .map { it.itemId }

    private suspend fun nextOf(itemIds: List<String>): String? =
        resolveMediaPlayTarget.nextOf(itemIds)

    private suspend fun randomOf(tileId: Long, itemIds: List<String>): String? {
        if (itemIds.isEmpty()) return null
        val watched = mediaRepository.getUserDataFor(itemIds)
        val held = randomChoices[tileId]
        if (held != null && held in itemIds && watched[held]?.played != true) return held
        val unplayed = itemIds.filterNot { watched[it]?.played == true }
        val chosen = (if (unplayed.isEmpty()) itemIds else unplayed).random()
        randomChoices[tileId] = chosen
        return chosen
    }

    private fun titleOf(item: MediaItemEntity): String = item.seriesName ?: item.name

    private fun subtitleOf(item: MediaItemEntity): String? {
        val season = item.parentIndexNumber
        val episode = item.indexNumber
        val marker = when {
            season != null && episode != null -> "S$season E$episode"
            episode != null -> "E$episode"
            else -> null
        }
        if (marker == null) return item.productionYear?.toString()
        return "$marker - ${item.name}"
    }
}
