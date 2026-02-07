package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey
    val id: Long,
    val slug: String,
    val fsSlug: String? = null,
    val name: String,
    val shortName: String,
    val sortOrder: Int = 0,
    val isVisible: Boolean = true,
    val logoPath: String? = null,
    val romExtensions: String,
    val lastScanned: Instant? = null,
    val gameCount: Int = 0,
    val syncEnabled: Boolean = true,
    val customRomPath: String? = null
)

fun PlatformEntity.getDisplayName(maxLength: Int? = null): String {
    if (maxLength == null || name.length <= maxLength) return name
    if (shortName.length <= maxLength) return shortName
    return shortName.take(maxLength - 1) + "…"
}
