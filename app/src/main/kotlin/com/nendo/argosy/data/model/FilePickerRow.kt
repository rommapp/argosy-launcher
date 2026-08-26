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

data class FilePickerSelection(val fileIds: Set<Long>, val versionIds: Set<Long>)

private fun List<FilePickerRow>.selectableRows(): List<FilePickerRow> =
    filter { !it.isHeader && !it.isLocked }

fun List<FilePickerRow>.allSelectableSelected(
    selectedFileIds: Set<Long>,
    selectedVersionIds: Set<Long>
): Boolean {
    val rows = selectableRows()
    return rows.isNotEmpty() && rows.all { row ->
        row.versionRommId?.let { it in selectedVersionIds } ?: (row.rommFileId in selectedFileIds)
    }
}

fun List<FilePickerRow>.selectAllSelection(): FilePickerSelection {
    val rows = selectableRows()
    return FilePickerSelection(
        fileIds = rows.mapNotNull { it.rommFileId }.toSet(),
        versionIds = rows.mapNotNull { it.versionRommId }.toSet()
    )
}

/**
 * Clearing keeps one version selected, matching what a group header does: a game with every
 * version deselected has no file to fetch and no default to fall back on.
 */
fun List<FilePickerRow>.selectNoneSelection(): FilePickerSelection {
    val rows = selectableRows()
    val keptVersion = rows.firstOrNull { it.isDefaultVersion }?.versionRommId
        ?: rows.firstNotNullOfOrNull { it.versionRommId }
    return FilePickerSelection(emptySet(), setOfNotNull(keptVersion))
}
