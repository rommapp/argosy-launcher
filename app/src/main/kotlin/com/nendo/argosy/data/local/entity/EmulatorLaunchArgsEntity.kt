package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-(platform, emulator) user overrides for launch intent construction. A null field means
 * "use the emulator's default for this field"; a non-null field overrides the default. All fields
 * null -> the row should be deleted so platform detail's Launch Args row shows "(defaults)".
 *
 * See the Launch Args modal in `LaunchArgsModal.kt` and the resolve path in
 * `GameLauncher.buildEffectiveCommand`.
 */
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
    /** "INTENT" | "SHELL" | null (use emulator's default) */
    val launchMethod: String? = null,
    /** "AUTO" | "ABSOLUTE_PATH" | "FILE_PROVIDER" | "DOCUMENT_URI" | null */
    val romPathFormat: String? = null,
    /** Full Intent flag bitmask; null = use emulator's default flags. */
    val intentFlagsMask: Int? = null,
    /** MIME type override for ACTION_VIEW launches; null = emulator's default. */
    val mimeType: String? = null
) {
    fun hasAnyOverride(): Boolean =
        launchMethod != null || romPathFormat != null || intentFlagsMask != null || mimeType != null
}
