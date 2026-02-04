package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "platform_libretro_settings",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("platformId", unique = true)]
)
data class PlatformLibretroSettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val platformId: Long,
    val shader: String? = null,
    val filter: String? = null,
    val aspectRatio: String? = null,
    val rotation: Int? = null,
    val overscanCrop: Int? = null,
    val blackFrameInsertion: Boolean? = null,
    val fastForwardSpeed: Int? = null,
    val rewindEnabled: Boolean? = null,
    val skipDuplicateFrames: Boolean? = null,
    val lowLatencyAudio: Boolean? = null,
    val analogAsDpad: Boolean? = null,
    val dpadAsAnalog: Boolean? = null,
    val rumbleEnabled: Boolean? = null
) {
    fun hasAnyOverrides(): Boolean = hasAnyVideoOverrides() || hasAnyControlOverrides()

    fun hasAnyVideoOverrides(): Boolean =
        shader != null ||
        filter != null ||
        aspectRatio != null ||
        rotation != null ||
        overscanCrop != null ||
        blackFrameInsertion != null ||
        fastForwardSpeed != null ||
        rewindEnabled != null ||
        skipDuplicateFrames != null ||
        lowLatencyAudio != null

    fun hasAnyControlOverrides(): Boolean =
        analogAsDpad != null || dpadAsAnalog != null || rumbleEnabled != null
}
