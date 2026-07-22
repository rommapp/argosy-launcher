package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bgm_playlist",
    indices = [
        Index(value = ["filePath"], unique = true)
    ]
)
data class BgmPlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val position: Int,
    val filePath: String,
    val displayName: String,
    val gameFileId: Long? = null,
    @ColumnInfo(defaultValue = "file")
    val entryType: String = TYPE_FILE,
    val sourceEntryId: Long? = null,
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean = true
) {
    companion object {
        const val TYPE_FILE = "file"
        const val TYPE_FOLDER = "folder"
    }
}
