package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.FILE_PICKER_UNCATEGORISED_GROUP_PREFIX
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.remote.romm.RomMRomFile
import java.util.TreeMap

data class FilePickerEntry(
    val rommFileId: Long,
    val fileName: String,
    val dirPath: String,
    val category: String?,
    val sizeBytes: Long,
    val isDownloaded: Boolean = false,
    val isLocked: Boolean = false
)

fun RomMRomFile.toPickerEntry(isDownloaded: Boolean): FilePickerEntry = FilePickerEntry(
    rommFileId = id,
    fileName = fileName,
    dirPath = filePath,
    category = category,
    sizeBytes = fileSizeBytes,
    isDownloaded = isDownloaded
)

fun GameFileEntity.toPickerEntry(rommFileId: Long, isDownloaded: Boolean, isLocked: Boolean): FilePickerEntry =
    FilePickerEntry(
        rommFileId = rommFileId,
        fileName = fileName,
        dirPath = filePath,
        category = category,
        sizeBytes = fileSize,
        isDownloaded = isDownloaded,
        isLocked = isLocked
    )

/**
 * The folder tree both the download picker and manage mode render. Base game files come first and
 * unheaded, then one node per category nested by path below that category's common folder, then
 * uncategorised folders nested by path below the game's common folder. A folder holding nothing
 * but one subfolder merges with it into a single row.
 */
object FilePickerTree {

    private const val CHAIN_LABEL_SEPARATOR = " / "
    private const val PATH_SEPARATOR = "/"
    private val GAME_KEY = VariantCategory.GAME.key
    private val NAME_ORDER: Comparator<String> = String.CASE_INSENSITIVE_ORDER.then(naturalOrder())

    fun build(entries: List<FilePickerEntry>, lockSoleBaseFile: Boolean): List<FilePickerRow> {
        if (entries.isEmpty()) return emptyList()
        val placed = entries.map { Placed(it, segmentsOf(it.dirPath)) }
        val rootDepth = placed.minOf { it.segments.size }
        val grouped = placed.groupBy { groupOf(it.entry, it.segments.size == rootDepth) }
        val rows = mutableListOf<FilePickerRow>()

        val baseFiles = grouped[GAME_KEY].orEmpty().map { it.entry }.sortedBy { it.fileName }
        val lockBase = lockSoleBaseFile && baseFiles.size == 1
        baseFiles.forEach { rows += fileRow(it, GAME_KEY, depth = 0, parentPath = null, lock = lockBase) }

        grouped.keys
            .filterNotNull()
            .filter { it != GAME_KEY }
            .sortedBy { VariantCategory.fromKey(it).sortOrder }
            .forEach { key ->
                val members = grouped.getValue(key)
                val root = trieOf(members, commonPrefix(members.map { it.segments }).size)
                rows += FilePickerRow(
                    isFolder = true,
                    groupKey = key,
                    label = VariantCategory.fromKey(key).displayLabel,
                    depth = 0,
                    folderPath = key,
                    fileCount = root.fileCount,
                    sizeBytes = root.totalSize
                )
                emitContents(root, key, key, depth = 1, rows)
            }

        grouped[null]?.let { members ->
            val root = trieOf(members, commonPrefix(placed.map { it.segments }).size)
            emitContents(root, path = null, groupKey = null, depth = 0, rows)
        }
        return rows
    }

    private class Placed(val entry: FilePickerEntry, val segments: List<String>)

    private class Node(val name: String) {
        val children = TreeMap<String, Node>(NAME_ORDER)
        val files = mutableListOf<FilePickerEntry>()
        val fileCount: Int by lazy { files.size + children.values.sumOf { it.fileCount } }
        val totalSize: Long by lazy { files.sumOf { it.sizeBytes } + children.values.sumOf { it.totalSize } }
    }

    private fun groupOf(entry: FilePickerEntry, atRoot: Boolean): String? {
        val category = VariantCategory.fromKey(entry.category)
        return when {
            category == VariantCategory.GAME -> GAME_KEY
            category != VariantCategory.UNKNOWN -> category.key
            atRoot -> GAME_KEY
            else -> null
        }
    }

    private fun segmentsOf(dirPath: String): List<String> =
        dirPath.split('/', '\\').filter { it.isNotEmpty() }

    private fun commonPrefix(paths: List<List<String>>): List<String> {
        val first = paths.firstOrNull() ?: return emptyList()
        var length = first.size
        for (path in paths) {
            var shared = 0
            while (shared < length && shared < path.size && path[shared] == first[shared]) shared++
            length = shared
        }
        return first.take(length)
    }

    private fun trieOf(members: List<Placed>, rootLength: Int): Node {
        val root = Node("")
        for (member in members) {
            var node = root
            for (segment in member.segments.drop(rootLength)) {
                node = node.children.getOrPut(segment) { Node(segment) }
            }
            node.files += member.entry
        }
        return root
    }

    private fun emitContents(node: Node, path: String?, groupKey: String?, depth: Int, rows: MutableList<FilePickerRow>) {
        node.children.values.forEach { emitFolder(it, path, groupKey, depth, rows) }
        val fileGroup = groupKey ?: return
        node.files.sortedBy { it.fileName }.forEach { rows += fileRow(it, fileGroup, depth, path, lock = false) }
    }

    private fun emitFolder(start: Node, parentPath: String?, groupKey: String?, depth: Int, rows: MutableList<FilePickerRow>) {
        val names = mutableListOf(start.name)
        var node = start
        while (node.files.isEmpty() && node.children.size == 1) {
            node = node.children.values.single()
            names += node.name
        }
        val relative = names.joinToString(PATH_SEPARATOR)
        val path = parentPath?.let { it + PATH_SEPARATOR + relative }
            ?: (FILE_PICKER_UNCATEGORISED_GROUP_PREFIX + relative)
        val group = groupKey ?: path
        rows += FilePickerRow(
            isFolder = true,
            groupKey = group,
            label = names.joinToString(CHAIN_LABEL_SEPARATOR),
            depth = depth,
            folderPath = path,
            parentPath = parentPath,
            fileCount = node.fileCount,
            sizeBytes = node.totalSize
        )
        emitContents(node, path, group, depth + 1, rows)
    }

    private fun fileRow(
        entry: FilePickerEntry,
        groupKey: String,
        depth: Int,
        parentPath: String?,
        lock: Boolean
    ): FilePickerRow = FilePickerRow(
        isFolder = false,
        groupKey = groupKey,
        label = entry.fileName,
        depth = depth,
        parentPath = parentPath,
        rommFileId = entry.rommFileId,
        sizeBytes = entry.sizeBytes,
        isDownloaded = entry.isDownloaded,
        isLocked = entry.isLocked || lock
    )
}
