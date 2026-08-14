package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What the server said about one playable version of one item, kept from the last time that version
 * was negotiated.
 *
 * This is a cache of the server's answer, not user data: it is owner scoped like the rest of the
 * media schema and a fresh negotiation replaces the row rather than adding to it. An item with
 * alternate versions carries one row per [mediaSourceId].
 *
 * Every field is nullable because the server reports what it has probed. A null is "not known", and
 * a reader must treat it as such - never as zero, and never as small.
 *
 * [bitrateKbps] and [videoHeight] are resolved at capture rather than stored raw, so the tier
 * comparison a download makes against a live negotiation and the one it makes against this cache are
 * the same comparison on the same units.
 */
@Entity(
    tableName = "media_sources",
    indices = [
        Index(value = ["ownerUserId", "itemId", "mediaSourceId"], unique = true)
    ]
)
data class MediaSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val mediaSourceId: String,
    val container: String? = null,
    val sizeBytes: Long? = null,
    val bitrateKbps: Int? = null,
    val videoHeight: Int? = null
)
