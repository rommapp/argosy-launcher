package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One person credited on one title.
 *
 * The person is denormalised onto every title they appear on rather than held once and joined: the
 * same name is wanted beside a title far more often than on its own, and the server sends the whole
 * credit inline with the item, so a shared person table would add a write path that can only ever
 * fall out of step with the items feeding it.
 *
 * [role] is the character an actor played and is null for crew, which is what tells a cast entry
 * apart from a crew one. [personId] addresses the PERSON on the server, so a portrait is requested
 * against it and not against [itemId]; it is also what lets two titles be recognised as sharing a
 * director without comparing names.
 *
 * [sortOrder] preserves the billing order the server sent. It is stored because that order is
 * editorial -- top billing first -- and cannot be recovered from anything else on the row.
 */
@Entity(
    tableName = "media_credits",
    indices = [
        Index(value = ["ownerUserId", "itemId", "personId", "personType"], unique = true),
        Index(value = ["ownerUserId", "itemId"]),
        Index(value = ["ownerUserId", "personId"])
    ]
)
data class MediaCreditEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val personId: String,
    val name: String,
    val role: String? = null,
    val personType: String,
    val sortOrder: Int = 0,
    val primaryImageTag: String? = null
)
