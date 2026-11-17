package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.FolderAppearance
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.StickerOutline
import com.nendo.argosy.domain.model.StickerOutlineColor
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FolderAppearanceEditor
import com.nendo.argosy.ui.components.FolderAppearanceRow
import com.nendo.argosy.ui.components.PicturePurpose
import com.nendo.argosy.ui.components.folderAppearanceRows
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Edits how a folder looks. The panel's state lives on [CustomGridState]; each change lands on the
 * grid's own tiles at once and is then written to the folder's row. [choosePicture] hands a picture
 * row to the decoration chooser.
 */
class FolderAppearanceController(
    private val scope: CoroutineScope,
    private val repository: HomeTileRepository?,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit,
    private val choosePicture: (PicturePurpose) -> Unit
) {

    private val writes = Mutex()

    fun open() {
        val tile = read().focusedTile ?: return
        if (tile.target !is HomeTileTargetRef.Folder) return
        write { it.copy(folderAppearance = FolderAppearanceEditor(folderId = tile.id), showMenu = false) }
    }

    fun close(): Boolean {
        if (read().folderAppearance == null) return false
        write { it.copy(folderAppearance = null) }
        return true
    }

    fun moveFocus(delta: Int) = write { state ->
        val editor = state.folderAppearance ?: return@write state
        val count = state.appearanceRows.size
        if (count == 0) return@write state
        state.copy(folderAppearance = editor.copy(focusIndex = (editor.focusIndex + delta).mod(count)))
    }

    fun focusRow(index: Int) = write { state ->
        val editor = state.folderAppearance ?: return@write state
        if (index !in state.appearanceRows.indices) return@write state
        state.copy(folderAppearance = editor.copy(focusIndex = index))
    }

    fun cycleFocused(delta: Int): Boolean {
        val row = read().focusedAppearanceRow ?: return false
        return cycle(row, delta)
    }

    fun cycle(row: FolderAppearanceRow, delta: Int): Boolean {
        val transform: (FolderAppearance) -> FolderAppearance = when (row) {
            FolderAppearanceRow.COVER -> { a -> a.copy(cover = a.cover.step(delta)) }
            FolderAppearanceRow.COVER_OUTLINE -> { a -> a.copy(coverOutline = a.coverOutline.stepChoice(delta)) }
            FolderAppearanceRow.SHOW_NAME -> { a -> a.copy(showName = delta > 0) }
            FolderAppearanceRow.BACKDROP -> { a -> a.copy(backdrop = a.backdrop.step(delta)) }
            FolderAppearanceRow.ACCENT -> { a -> a.copy(accent = a.accent.step(delta)) }
            FolderAppearanceRow.MEMBER_SIZE -> { a -> a.copy(memberSize = a.memberSize.step(delta)) }
            FolderAppearanceRow.COVER_PICTURE, FolderAppearanceRow.BACKDROP_PICTURE -> return false
        }
        return update(transform)
    }

    fun confirmFocused(): Boolean {
        val row = read().focusedAppearanceRow ?: return false
        return activate(row)
    }

    fun activate(row: FolderAppearanceRow): Boolean {
        val folderId = read().folderAppearance?.folderId ?: return false
        return when (row) {
            FolderAppearanceRow.COVER_PICTURE -> {
                choosePicture(PicturePurpose.FolderCover(folderId))
                true
            }
            FolderAppearanceRow.BACKDROP_PICTURE -> {
                choosePicture(PicturePurpose.FolderBackdrop(folderId))
                true
            }
            FolderAppearanceRow.SHOW_NAME -> update { it.copy(showName = !it.showName) }
            else -> false
        }
    }

    fun setCoverSource(folderId: Long, source: TileImageSource) =
        update(folderId) { it.copy(coverSource = source) }

    fun setBackdropSource(folderId: Long, source: TileImageSource) =
        update(folderId) { it.copy(backdropSource = source) }

    private fun update(transform: (FolderAppearance) -> FolderAppearance): Boolean {
        val folderId = read().folderAppearance?.folderId ?: return false
        return update(folderId, transform)
    }

    private fun update(folderId: Long, transform: (FolderAppearance) -> FolderAppearance): Boolean {
        val tile = read().tiles.firstOrNull { it.id == folderId } ?: return false
        val folder = tile.target as? HomeTileTargetRef.Folder ?: return false
        val retargeted = folder.copy(appearance = transform(folder.appearance))
        write { state ->
            val tiles = state.tiles.map { if (it.id == folderId) it.copy(target = retargeted) else it }
            val editor = state.folderAppearance
            val rowCount = folderAppearanceRows(retargeted.appearance).size
            state.copy(
                tiles = tiles,
                folderAppearance = editor?.copy(focusIndex = editor.focusIndex.coerceIn(0, rowCount - 1))
            )
        }
        val tiles = repository ?: return true
        scope.launch { writes.withLock { tiles.retarget(folderId, retargeted, emptyList()) } }
        return true
    }
}

private inline fun <reified T : Enum<T>> T.step(delta: Int): T {
    val values = enumValues<T>()
    return values[(ordinal + delta).mod(values.size)]
}

private val OUTLINE_CHOICES: List<StickerOutlineColor?> = listOf(null) + StickerOutlineColor.entries

private fun StickerOutline.stepChoice(delta: Int): StickerOutline {
    val current = OUTLINE_CHOICES.indexOf(if (enabled) color else null)
    val next = OUTLINE_CHOICES[(current + delta).mod(OUTLINE_CHOICES.size)]
    return if (next == null) copy(enabled = false) else copy(enabled = true, color = next)
}
