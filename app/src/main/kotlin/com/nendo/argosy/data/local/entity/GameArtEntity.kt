package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.model.resolveArtPath

@Entity(
    tableName = "game_art",
    primaryKeys = ["gameId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["changeSeq"])]
)
data class GameArtEntity(
    val gameId: Long,
    val slot: String,
    val sourceUrl: String? = null,
    val cachedPath: String? = null,
    val cachedFromUrl: String? = null,
    val overridePath: String? = null,
    val gradientColors: String? = null,
    val coverAspectRatio: Float? = null,
    @ColumnInfo(defaultValue = "0")
    val changeSeq: Long = 0
) {
    val resolvedPath: String? get() = resolveArtPath(overridePath, cachedPath, sourceUrl)

    val artSlot: ArtSlot? get() = ArtSlot.entries.firstOrNull { it.name == slot }
}

fun Collection<GameArtEntity>.toResolvedArt(): ResolvedGameArt {
    val bySlot = associateBy { it.slot }
    val cover = bySlot[ArtSlot.COVER.name]
    return ResolvedGameArt(
        coverPath = cover?.resolvedPath,
        backgroundPath = bySlot[ArtSlot.BACKGROUND.name]?.resolvedPath,
        logoPath = bySlot[ArtSlot.LOGO.name]?.resolvedPath,
        box3dPath = bySlot[ArtSlot.BOX_3D.name]?.resolvedPath,
        boxSpinePath = bySlot[ArtSlot.BOX_SPINE.name]?.resolvedPath,
        boxBackPath = bySlot[ArtSlot.BOX_BACK.name]?.resolvedPath,
        gradientColors = cover?.gradientColors,
        coverAspectRatio = cover?.coverAspectRatio,
        overriddenSlots = mapNotNull { row -> row.artSlot?.takeIf { row.overridePath != null } }.toSet()
    )
}

fun Collection<GameArtEntity>.toResolvedArtByGame(): Map<Long, ResolvedGameArt> =
    groupBy { it.gameId }.mapValues { (_, rows) -> rows.toResolvedArt() }
