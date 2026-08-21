package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Every media item in one table: a movie, a series, one of its seasons, or one episode.
 *
 * The hierarchy is carried by [parentId], which holds the server item id of the row above -- the
 * series for a season, the season for an episode -- and is null for a movie or a series root.
 * [seriesId] is denormalised onto seasons and episodes so a series can gather its episodes without
 * walking the chain a level at a time.
 *
 * [parentId] and [libraryId] are deliberately not foreign keys. Episodes arrive from endpoints that
 * answer with an episode alone (Next Up is the standing case), so the season and the library it
 * belongs to may never have been synced; a foreign key would reject the row and lose the item
 * rather than store it unresolved. Resolution happens on read, and a parent that is missing is a
 * reportable state instead of an insert that throws.
 *
 * [localPath] is set when a copy has been downloaded and is the item's own record of where its file
 * lives, so the download queue row can be cleared without losing the fact that the item is offline
 * capable. An unreadable volume leaves the path in place: unreadable is not absent.
 *
 * [tmdbId], [imdbId] and [tvdbId] are the item's identity outside this server, and are what lets
 * anything off-device name the title without being handed the library's own artwork. They are
 * absent for a title the server's metadata agent never matched -- a home video, a mislabelled rip --
 * and no re-sync will fill them, so a consumer needs a route that works without them.
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["ownerUserId", "itemId"], unique = true),
        Index(value = ["ownerUserId", "libraryId"]),
        Index(value = ["ownerUserId", "parentId"]),
        Index(value = ["ownerUserId", "seriesId"]),
        Index(value = ["ownerUserId", "sortName"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val libraryId: String? = null,
    val parentId: String? = null,
    val seriesId: String? = null,
    val itemType: String,
    val name: String,
    val sortName: String,
    val overview: String? = null,
    val productionYear: Int? = null,
    val premiereDate: Instant? = null,
    val dateCreated: Instant? = null,
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val genres: String? = null,
    val studios: String? = null,
    val runTimeTicks: Long? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val seriesName: String? = null,
    val childCount: Int? = null,
    val primaryImageTag: String? = null,
    val backdropImageTag: String? = null,
    val thumbImageTag: String? = null,
    val container: String? = null,
    val localPath: String? = null,
    val downloadQuality: String? = null,
    val downloadedBytes: Long? = null,
    val downloadedAt: Instant? = null,
    val lastSyncedAt: Instant? = null
)

/**
 * Which level of the hierarchy a row sits at. Stored as the server's own item-kind token so a kind
 * from a newer server reads back as unresolved instead of throwing.
 */
enum class MediaItemType(val wireValue: String) {
    MOVIE("Movie"),
    SERIES("Series"),
    SEASON("Season"),
    EPISODE("Episode");

    companion object {
        fun fromWire(value: String?): MediaItemType? =
            entries.firstOrNull { it.wireValue == value }
    }
}
