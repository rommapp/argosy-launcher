package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "game_screenshots",
    primaryKeys = ["gameId", "position"],
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
    val cachedFromUrl: String? = null
) {
    val isCachedFromSource: Boolean get() = cachedPath != null && cachedFromUrl == sourceUrl
}

/**
 * The rows [gameId] holds after its screenshot source list becomes [urls]. Blank urls are
 * skipped, so an empty or null list leaves no rows. A position whose url is unchanged keeps its
 * row from [existing], cache included; any other position starts uncached.
 */
fun planScreenshotRows(
    gameId: Long,
    existing: Collection<GameScreenshotEntity>,
    urls: List<String>?
): List<GameScreenshotEntity> {
    val byPosition = existing.associateBy { it.position }
    return urls.orEmpty().filter { it.isNotBlank() }.mapIndexed { position, url ->
        byPosition[position]?.takeIf { it.sourceUrl == url } ?: GameScreenshotEntity(gameId, position, url)
    }
}
