package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Where a curated grid page's own settings live. A page is a thing rather than a position, so a
 * background or a sound stays with the page it was set on when the pages around it are moved or
 * removed.
 */
@Entity(
    tableName = "home_grid_pages",
    indices = [Index(value = ["ownerUserId", "sortOrder"], unique = true)]
)
data class HomeGridPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long?,
    val sortOrder: Int,
    val name: String? = null,
    val backgroundKind: String = PageBackgroundKind.NONE.name,
    val backgroundPath: String? = null,
    val backgroundGameId: Long? = null,
    val audioKind: String = PageAudioKind.GLOBAL.name,
    val audioPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * [GAME_ART] draws artwork already held for a game in the library, so a page can be themed without
 * bringing a second copy of the image onto the device.
 */
enum class PageBackgroundKind {
    NONE,
    FILE,
    GAME_ART;

    companion object {
        fun fromString(value: String?): PageBackgroundKind =
            entries.find { it.name == value } ?: NONE
    }
}

/**
 * What the page does about sound. [TILE] hands the output to whatever video tile is playing on the
 * page, and [THEME] plays the page's own file; both stand in for the launcher's music while the
 * page is shown.
 */
enum class PageAudioKind {
    GLOBAL,
    THEME,
    TILE;

    companion object {
        fun fromString(value: String?): PageAudioKind =
            entries.find { it.name == value } ?: GLOBAL
    }
}
