package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emulator_launch_args",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["platformId", "emulatorId"], unique = true)]
)
data class EmulatorLaunchArgsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val platformId: Long,
    val emulatorId: String,
    val launchMethod: String? = null,
    val romPathFormat: String? = null,
    val intentFlagsMask: Int? = null,
    val mimeType: String? = null,
    val dataBinding: String? = null,
    val extraBinding: String? = null,
    val clipDataBinding: String? = null,
    val customExtras: String? = null
) {
    fun hasAnyOverride(): Boolean =
        launchMethod != null ||
            intentFlagsMask != null ||
            mimeType != null ||
            dataBinding != null ||
            extraBinding != null ||
            clipDataBinding != null ||
            !customExtras.isNullOrBlank()
}
