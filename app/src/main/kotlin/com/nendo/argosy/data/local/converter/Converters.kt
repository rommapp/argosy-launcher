package com.nendo.argosy.data.local.converter

import androidx.room.TypeConverter
import com.nendo.argosy.data.local.entity.SocialSyncStatus
import com.nendo.argosy.data.local.entity.SocialSyncType
import com.nendo.argosy.data.model.GameSource
import java.time.Instant

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromGameSource(source: GameSource): String = source.name

    @TypeConverter
    fun toGameSource(value: String): GameSource = GameSource.valueOf(value)

    @TypeConverter
    fun fromSocialSyncType(type: SocialSyncType): String = type.name

    @TypeConverter
    fun toSocialSyncType(value: String): SocialSyncType = SocialSyncType.valueOf(value)

    @TypeConverter
    fun fromSocialSyncStatus(status: SocialSyncStatus): String = status.name

    @TypeConverter
    fun toSocialSyncStatus(value: String): SocialSyncStatus = SocialSyncStatus.valueOf(value)
}
