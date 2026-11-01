package com.nendo.argosy.data.download

import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.FileNames
import java.io.File

internal data class DownloadRequestIdentity(
    val gameId: Long,
    val resourceId: Long,
    val discId: Long?,
    val gameFileId: Long?,
    val fileName: String,
    val platform: String,
    val multiFile: Boolean,
    val selection: Set<String>?,
    val folder: String?,
    val category: String?,
    val discNumber: Int?
) {
    fun sharesSlot(other: DownloadRequestIdentity): Boolean = when {
        discId != null -> other.discId == discId
        gameFileId != null -> other.discId == null && other.gameFileId == gameFileId
        else -> other.discId == null && other.gameFileId == null && other.gameId == gameId
    }

    companion object {
        fun from(row: DownloadQueueEntity): DownloadRequestIdentity {
            val selection = row.selectedFileIds?.let {
                if (it.isEmpty()) emptySet() else it.split(',').map { token ->
                    val trimmed = token.trim()
                    trimmed.toLongOrNull()?.toString() ?: trimmed
                }.toSet()
            }
            return DownloadRequestIdentity(
                row.gameId, row.rommId, row.discId, row.gameFileId,
                FileNames.sanitize(row.fileName), PlatformDefinitions.getCanonicalSlug(row.platformSlug),
                row.isMultiFileRom || (selection?.size ?: 0) > 1, selection,
                row.gameFolderName, row.fileCategory, row.discNumber
            )
        }
    }
}

internal fun DownloadQueueEntity.transferFiles(intendedPart: File): Set<File> =
    listOfNotNull(tempFilePath?.let(::File), intendedPart).flatMap { part ->
        listOf(part.canonicalFile, File(part.parentFile, fileName).canonicalFile)
    }.toSet()
