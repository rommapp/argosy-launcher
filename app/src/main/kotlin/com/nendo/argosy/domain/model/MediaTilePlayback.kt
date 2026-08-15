package com.nendo.argosy.domain.model

/**
 * What a media tile plays right now.
 *
 * The three answers are kept apart because two of them look the same from the tile's own row and
 * mean opposite things. [Pending] is a tile that knows exactly what it stands for and is waiting for
 * the file to arrive, which is a state worth drawing as itself; [Unresolved] is a tile nothing
 * playable could be found for at all, which is the only one that should read as broken.
 *
 * A tile plays from this device or it does not play. That is why [Ready] carries a path rather than
 * a flag: there is no branch downstream that could stream instead, so the absence of a local file is
 * the whole of what makes a target unplayable.
 */
sealed interface MediaTilePlayback {

    /**
     * [itemId] is null for a tile pointing at a file on this device, which belongs to no library and
     * has no watch state to resume from.
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
 * Why a resolved target is not playable yet. [STORAGE_UNAVAILABLE] is a copy that was downloaded onto
 * storage nobody has connected right now, which is not the same as never having been fetched, and a
 * tile offering to download it again would spend the transfer twice.
 */
enum class MediaTilePendingReason {
    NOT_DOWNLOADED,
    STORAGE_UNAVAILABLE
}
