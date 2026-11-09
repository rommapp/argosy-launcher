package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Stored `game_screenshots.source` tokens. [OFFICIAL] rows come from metadata providers and are
 * rewritten by library sync; [OWN] and [COMMUNITY] rows are RomM gallery shots uploaded by the
 * signed-in user and by other users who made theirs public.
 */
object ScreenshotSource {
    const val OFFICIAL = "official"
    const val OWN = "own"
    const val COMMUNITY = "community"

    fun displayRank(source: String): Int = when (source) {
        OWN -> 0
        COMMUNITY -> 1
        else -> 2
    }
}

@Entity(
    tableName = "game_screenshots",
    primaryKeys = ["gameId", "source", "position"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class GameScreenshotEntity(
    val gameId: Long,
    val position: Int,
    val sourceUrl: String,
    val cachedPath: String? = null,
    val cachedFromUrl: String? = null,
    @ColumnInfo(defaultValue = ScreenshotSource.OFFICIAL)
    val source: String = ScreenshotSource.OFFICIAL,
    val rommScreenshotId: Long? = null,
    val ownerUsername: String? = null
) {
    val isCachedFromSource: Boolean get() = cachedPath != null && cachedFromUrl == sourceUrl
    val isOfficial: Boolean get() = source == ScreenshotSource.OFFICIAL
}

/**
 * The official rows [gameId] holds after its screenshot source list becomes [urls]. Blank urls
 * are skipped, so an empty or null list leaves no rows. A position whose url is unchanged keeps
 * its row from [existing], cache included; any other position starts uncached. Gallery rows in
 * [existing] are ignored.
 */
fun planScreenshotRows(
    gameId: Long,
    existing: Collection<GameScreenshotEntity>,
    urls: List<String>?
): List<GameScreenshotEntity> {
    val byPosition = existing.filter { it.isOfficial }.associateBy { it.position }
    return urls.orEmpty().filter { it.isNotBlank() }.mapIndexed { position, url ->
        byPosition[position]?.takeIf { it.sourceUrl == url } ?: GameScreenshotEntity(gameId, position, url)
    }
}

/**
 * [rows] in the order the details page shows them: the user's own gallery shots, then other
 * users' public ones, then the official screenshots, each group by position.
 */
fun List<GameScreenshotEntity>.inDisplayOrder(): List<GameScreenshotEntity> =
    sortedWith(compareBy({ ScreenshotSource.displayRank(it.source) }, { it.position }))
