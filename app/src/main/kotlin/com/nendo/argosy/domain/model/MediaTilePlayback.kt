package com.nendo.argosy.domain.model

/**
 * What a media tile plays right now.
 *
 * [Pending] knows what it stands for and is waiting on the file; [Unresolved] found nothing at all
 * and is the only one that should read as broken.
 */
sealed interface MediaTilePlayback {

    /**
     * [itemId] is null for a local file, which belongs to no library.
     */
    data class Ready(
        val localPath: String,
        val itemId: String?,
        val title: String,
        val subtitle: String?,
        val resumeTicks: Long = 0
    ) : MediaTilePlayback

    data class Pending(
        val itemId: String,
        val title: String,
        val subtitle: String?,
        val reason: MediaTilePendingReason
    ) : MediaTilePlayback

    data object Unresolved : MediaTilePlayback

    val playableItemId: String?
        get() = (this as? Ready)?.itemId

    val playableLocalPath: String?
        get() = (this as? Ready)?.localPath
}

/**
 * Why a resolved target is not playable yet. [STORAGE_UNAVAILABLE] is downloaded but on storage that
 * is not connected, which is not the same as never fetched.
 */
enum class MediaTilePendingReason {
    NOT_DOWNLOADED,
    STORAGE_UNAVAILABLE
}
