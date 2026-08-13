package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One media-server library the signed-in media account can see.
 *
 * [ownerUserId] is the media server's user id, not a RomM account id. Library visibility is decided
 * per user by the server, so two accounts on one device legitimately hold different rows for the
 * same server library and every read is scoped to one of them. It is never null: nothing in this
 * family exists before a login, so there is no pre-account row to adopt.
 *
 * [libraryId] is the server's own item id for the library and is the identity every other media
 * table references, so a child row can be written without first resolving a local row id.
 */
@Entity(
    tableName = "media_libraries",
    indices = [
        Index(value = ["ownerUserId", "libraryId"], unique = true)
    ]
)
data class MediaLibraryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val libraryId: String,
    val name: String,
    val collectionType: String? = null,
    val primaryImageTag: String? = null,
    val itemCount: Int = 0,
    val displayOrder: Int = 0,
    val lastSyncedAt: Instant? = null
)

/**
 * What a library holds. Stored as the server's own token so a collection type from a newer server
 * reads back as unresolved instead of throwing.
 */
enum class MediaCollectionType(val wireValue: String) {
    MOVIES("movies"),
    TV_SHOWS("tvshows");

    companion object {
        fun fromWire(value: String?): MediaCollectionType? =
            entries.firstOrNull { it.wireValue == value }
    }
}
