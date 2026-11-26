package com.nendo.argosy.data.model

const val FILE_PICKER_UNCATEGORISED_GROUP_PREFIX = "folder:"

private const val FOLDER_EXPANDED_MAX_FILES = 10

/**
 * One line of the file picker tree, in pre-order. A folder row carries its own [folderPath] key
 * plus the file count and total size of everything beneath it; [parentPath] is the key of the
 * nearest enclosing folder, null for the unheaded base game rows and the top-level folders.
 */
data class FilePickerRow(
    val isFolder: Boolean,
    val groupKey: String,
    val label: String,
    val depth: Int = 0,
    val folderPath: String? = null,
    val parentPath: String? = null,
    val fileCount: Int = 0,
    val rommFileId: Long? = null,
    val sizeBytes: Long = 0,
    val isDownloaded: Boolean = false,
    val isLocked: Boolean = false
) {
    val isUncategorised: Boolean
        get() = groupKey.startsWith(FILE_PICKER_UNCATEGORISED_GROUP_PREFIX)

    fun isWithin(folder: String): Boolean =
        parentPath != null && (parentPath == folder || parentPath.startsWith("$folder/"))
}

enum class FolderCheckState { ALL, SOME, NONE }

fun FilePickerRow.countsAsSelected(selectedFileIds: Set<Long>): Boolean =
    (isLocked && isDownloaded) || rommFileId in selectedFileIds

fun List<FilePickerRow>.visibleWithCollapsed(collapsed: Set<String>): List<FilePickerRow> {
    if (collapsed.isEmpty()) return this
    val visible = ArrayList<FilePickerRow>(size)
    var hiddenUnder: String? = null
    for (row in this) {
        val under = hiddenUnder
        if (under != null && row.isWithin(under)) continue
        hiddenUnder = row.folderPath?.takeIf { it in collapsed }
        visible += row
    }
    return visible
}

/**
 * Folders whose top-level group holds [FOLDER_EXPANDED_MAX_FILES] files or more start collapsed,
 * nested folders included; smaller groups open fully.
 */
fun List<FilePickerRow>.initiallyCollapsedFolders(): Set<String> {
    val groupSizes = filter { it.isFolder && it.depth == 0 }.associate { it.groupKey to it.fileCount }
    return filter { it.isFolder && (groupSizes[it.groupKey] ?: 0) >= FOLDER_EXPANDED_MAX_FILES }
        .mapNotNull { it.folderPath }
        .toSet()
}

fun List<FilePickerRow>.folderCheckStates(selectedFileIds: Set<Long>): Map<String, FolderCheckState> {
    val folderKeys = mapNotNullTo(HashSet()) { it.folderPath }
    val totals = HashMap<String, Int>()
    val chosen = HashMap<String, Int>()
    for (row in this) {
        if (row.isFolder) continue
        val parent = row.parentPath ?: continue
        val isChosen = row.countsAsSelected(selectedFileIds)
        for (key in pathAndAncestors(parent)) {
            if (key !in folderKeys) continue
            totals.merge(key, 1, Int::plus)
            if (isChosen) chosen.merge(key, 1, Int::plus)
        }
    }
    return folderKeys.associateWith { key ->
        val total = totals[key] ?: 0
        val picked = chosen[key] ?: 0
        when {
            picked == 0 -> FolderCheckState.NONE
            picked == total -> FolderCheckState.ALL
            else -> FolderCheckState.SOME
        }
    }
}

private fun pathAndAncestors(path: String): List<String> {
    val keys = mutableListOf<String>()
    var end = path.indexOf('/')
    while (end >= 0) {
        keys += path.substring(0, end)
        end = path.indexOf('/', end + 1)
    }
    keys += path
    return keys
}

private fun FilePickerRow.isSelectable(): Boolean = !isFolder && !isLocked

private fun List<FilePickerRow>.selectableRows(): List<FilePickerRow> = filter { it.isSelectable() }

fun List<FilePickerRow>.allSelectableSelected(selectedFileIds: Set<Long>): Boolean {
    val rows = selectableRows()
    return rows.isNotEmpty() && rows.all { it.rommFileId in selectedFileIds }
}

fun List<FilePickerRow>.selectableFileIds(): Set<Long> =
    selectableRows().mapNotNull { it.rommFileId }.toSet()

fun List<FilePickerRow>.selectableFileIdsWithin(folder: String): Set<Long> =
    filter { it.isSelectable() && it.isWithin(folder) }.mapNotNull { it.rommFileId }.toSet()
