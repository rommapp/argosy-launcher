package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.FOLDER_MEMBER_PAGE_INDEX
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FolderChooserState
import com.nendo.argosy.ui.components.FolderNamingState
import com.nendo.argosy.ui.components.FolderOverlayState
import com.nendo.argosy.ui.components.columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Opens, fills and names the custom grid's folders. Owns no state of its own: the overlay, the
 * folder chooser and the naming prompt live on [CustomGridState], so the gamepad and touch reach them
 * through the same reads and writes as the rest of the grid.
 *
 * [autoFolderGames] answers the games an auto-folder's rule matches, in library order; null on a
 * surface that cannot query the library, where an auto-folder opens empty.
 */
class FolderController(
    private val scope: CoroutineScope,
    private val repository: HomeTileRepository?,
    private val ownerUserId: suspend () -> Long?,
    private val autoFolderGames: (suspend (LibraryLinkFilters) -> List<Long>)?,
    private val defaultFolderName: () -> String,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit
) {

    fun open(folder: HomeTile) {
        val target = folder.target as? HomeTileTargetRef.Folder ?: return
        val rule = target.rule
        write {
            it.copy(
                folderOverlay = FolderOverlayState(
                    folderId = folder.id,
                    autoMembers = if (rule == null) null else emptyList(),
                    isLoading = rule != null && autoFolderGames != null
                ),
                showMenu = false,
                engagedTileId = null
            )
        }
        if (rule != null) refreshAutoMembers(folder, rule)
    }

    private fun refreshAutoMembers(folder: HomeTile, rule: LibraryLinkFilters) {
        val resolve = autoFolderGames ?: return
        scope.launch {
            val members = resolve(rule).mapIndexed { index, gameId ->
                HomeTile(
                    id = -(index + 1L),
                    pageIndex = FOLDER_MEMBER_PAGE_INDEX,
                    rect = TileRect(index, 0),
                    target = HomeTileTargetRef.Game(gameId),
                    kind = folder.kind,
                    parentTileId = folder.id
                )
            }
            write { state ->
                val overlay = state.folderOverlay?.takeIf { it.folderId == folder.id } ?: return@write state
                state.copy(
                    folderOverlay = overlay.copy(
                        autoMembers = members,
                        isLoading = false,
                        focusIndex = overlay.focusIndex.coerceIn(0, (members.size - 1).coerceAtLeast(0))
                    )
                )
            }
        }
    }

    fun close(): Boolean {
        if (read().folderOverlay == null) return false
        write { it.copy(folderOverlay = null) }
        return true
    }

    /**
     * Moves the overlay's cursor. Left and right wrap through the whole folder; up and down step a
     * row and stop at either end. Answers false when the press went nowhere.
     */
    fun moveFocus(direction: GridDirection2D): Boolean {
        val current = read()
        val overlay = current.folderOverlay ?: return false
        val count = current.openFolderMembers.size
        if (count == 0) return false
        val columns = current.openFolderAppearance.memberSize.columns
        val target = when (direction) {
            GridDirection2D.LEFT -> (overlay.focusIndex - 1).mod(count)
            GridDirection2D.RIGHT -> (overlay.focusIndex + 1).mod(count)
            GridDirection2D.UP -> (overlay.focusIndex - columns).takeIf { it >= 0 }
            GridDirection2D.DOWN -> (overlay.focusIndex + columns).takeIf { it < count }
        } ?: return false
        if (target == overlay.focusIndex) return false
        write { state -> state.copy(folderOverlay = state.folderOverlay?.copy(focusIndex = target)) }
        return true
    }

    fun focusMember(index: Int) = write { state ->
        val overlay = state.folderOverlay ?: return@write state
        if (index !in state.openFolderMembers.indices) return@write state
        state.copy(folderOverlay = overlay.copy(focusIndex = index))
    }

    /**
     * Returns the focused member of a hand-filled folder to the grid. An auto-folder's members come
     * from its rule, so there is nothing to take out of one.
     */
    fun removeFocusedMember(): Boolean {
        val current = read()
        if (current.isOpenFolderAuto) return false
        val member = current.focusedFolderMember ?: return false
        val tiles = repository ?: return false
        val remaining = current.openFolderMembers.size - 1
        write { state ->
            val overlay = state.folderOverlay ?: return@write state
            state.copy(
                folderOverlay = overlay.copy(
                    focusIndex = overlay.focusIndex.coerceAtMost((remaining - 1).coerceAtLeast(0))
                )
            )
        }
        val layout = current.layout
        scope.launch { tiles.removeFromFolder(member.id, layout) }
        return true
    }

    fun openChooser() {
        val tile = read().focusedTile ?: return
        if (tile.target is HomeTileTargetRef.Folder) return
        write { it.copy(folderChooser = FolderChooserState(tileId = tile.id), showMenu = false) }
    }

    fun closeChooser() = write { it.copy(folderChooser = null) }

    fun moveChooserFocus(delta: Int) = write { state ->
        val chooser = state.folderChooser ?: return@write state
        val rows = state.folderChooserTargets.size + 1
        state.copy(folderChooser = chooser.copy(focusIndex = (chooser.focusIndex + delta).mod(rows)))
    }

    /**
     * Acts on a chooser row: the first starts naming a new folder that the tile will move into, any
     * other moves the tile into that folder.
     */
    fun confirmChooser(index: Int? = null) {
        val current = read()
        val chooser = current.folderChooser ?: return
        val row = index ?: chooser.focusIndex
        if (row == 0) {
            write {
                it.copy(
                    folderChooser = null,
                    folderNaming = FolderNamingState(
                        folderId = null,
                        initialName = defaultFolderName(),
                        moveTileId = chooser.tileId
                    )
                )
            }
            return
        }
        val folder = current.folderChooserTargets.getOrNull(row - 1) ?: return
        write { it.copy(folderChooser = null) }
        val tiles = repository ?: return
        scope.launch { tiles.moveIntoFolder(chooser.tileId, folder.id) }
    }

    /**
     * Starts naming a new folder for the cursor's cell. [rule] makes it an auto-folder.
     */
    fun beginCreate(rule: LibraryLinkFilters? = null) = write {
        it.copy(folderNaming = FolderNamingState(folderId = null, initialName = defaultFolderName(), rule = rule))
    }

    fun beginRename() {
        val tile = read().focusedTile ?: return
        val folder = tile.target as? HomeTileTargetRef.Folder ?: return
        write {
            it.copy(
                showMenu = false,
                folderNaming = FolderNamingState(folderId = tile.id, initialName = folder.name, rule = folder.rule)
            )
        }
    }

    fun cancelNaming() = write { it.copy(folderNaming = null) }

    /**
     * Settles the naming prompt. A rename rewrites the folder's name and keeps its rule. A new folder
     * lands on the cursor's cell, or on the cell of the tile it was made for, which then moves inside.
     */
    fun submitName(name: String) {
        val current = read()
        val naming = current.folderNaming ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write { it.copy(folderNaming = null) }
        val tiles = repository ?: return
        val renaming = naming.folderId
        if (renaming != null) {
            val existing = current.tiles.firstOrNull { it.id == renaming }?.target as? HomeTileTargetRef.Folder
                ?: return
            scope.launch { tiles.retarget(renaming, existing.copy(name = trimmed), emptyList()) }
            return
        }
        val moving = naming.moveTileId?.let { id -> current.tilesOnPage(current.page).firstOrNull { it.id == id } }
        val anchor = moving?.rect ?: TileRect(current.cell.columnIndex, current.cell.rowIndex)
        scope.launch {
            val folderId = tiles.place(
                ownerUserId = ownerUserId(),
                kind = current.kind,
                pageIndex = moving?.pageIndex ?: current.page,
                rect = TileRect(anchor.columnIndex, anchor.rowIndex),
                target = HomeTileTargetRef.Folder(name = trimmed, rule = naming.rule)
            )
            if (moving != null) tiles.moveIntoFolder(moving.id, folderId)
        }
    }

    /**
     * Rewrites an auto-folder's rule, keeping its name.
     */
    fun updateRule(folderId: Long, rule: LibraryLinkFilters) {
        val current = read()
        val existing = current.tiles.firstOrNull { it.id == folderId }?.target as? HomeTileTargetRef.Folder ?: return
        val tiles = repository ?: return
        scope.launch { tiles.retarget(folderId, existing.copy(rule = rule), emptyList()) }
    }

    fun deleteFolder(folder: HomeTile) {
        val tiles = repository ?: return
        val layout = read().layout
        scope.launch { tiles.deleteFolder(folder.id, layout) }
    }
}
