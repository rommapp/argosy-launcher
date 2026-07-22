package com.nendo.argosy.data.model

data class FilePickerRow(
    val isHeader: Boolean,
    val groupKey: String,
    val label: String,
    val rommFileId: Long? = null,
    val versionRommId: Long? = null,
    val sizeBytes: Long = 0,
    val isDownloaded: Boolean = false,
    val isDefaultVersion: Boolean = false,
    val isLocked: Boolean = false
)

fun List<FilePickerRow>.visibleWithCollapsed(collapsed: Set<String>): List<FilePickerRow> =
    filter { it.isHeader || it.groupKey !in collapsed }
