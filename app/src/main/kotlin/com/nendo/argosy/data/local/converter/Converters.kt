package com.nendo.argosy.data.local.converter

import androidx.room.TypeConverter
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
}
