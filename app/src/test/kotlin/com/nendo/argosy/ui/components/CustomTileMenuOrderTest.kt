package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.domain.model.TileRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile menu reads in three zones -- what this tile is, then this page, then what destroys
 * something -- and the destructive rows have to stay one contiguous run at the end, because the
 * modal fences them by a single start index rather than per row.
 */
class CustomTileMenuOrderTest {

    private fun tile(target: HomeTileTargetRef, coverScale: TileCoverScale = TileCoverScale.CROP) =
        HomeTile(id = 1L, pageIndex = 0, rect = TileRect(0, 0), target = target, coverScale = coverScale)

    private fun stateWith(
        target: HomeTileTargetRef?,
        pages: Int = 2,
        coverScale: TileCoverScale = TileCoverScale.CROP,
        supportsRaTileSetup: Boolean = true,
        raTile: RaTileStatus = RaTileStatus()
    ) = CustomGridState(
        tiles = target?.let { listOf(tile(it, coverScale)) }.orEmpty(),
        page = 0,
        cell = GridCell(0, 0),
        columns = 4,
        rows = 4,
        storedPages = pages,
        supportsRaTileSetup = supportsRaTileSetup,
        raTile = raTile
    )

    private val game = HomeTileTargetRef.Game(7L)

    @Test
    fun `a game tile reads tile rows, then page rows, then the destructive pair`() {
        val actions = stateWith(game).menuActions

        assertEquals(
            listOf(
                CustomTileMenuAction.FIT_COVER,
                CustomTileMenuAction.ARRANGE,
                CustomTileMenuAction.MOVE_TO_FOLDER,
                CustomTileMenuAction.PAGE_BACKDROP,
                CustomTileMenuAction.PAGE_MUSIC,
                CustomTileMenuAction.REMOVE,
                CustomTileMenuAction.DELETE_PAGE
            ),
            actions
        )
    }

    @Test
    fun `the cover row offers the opposite of the tile's current fit`() {
        assertTrue(
            CustomTileMenuAction.CROP_COVER in
                stateWith(game, coverScale = TileCoverScale.FIT).menuActions
        )
        assertTrue(
            CustomTileMenuAction.FIT_COVER in
                stateWith(game, coverScale = TileCoverScale.CROP).menuActions
        )
    }

    @Test
    fun `browsing achievements leads the menu, ahead of reconfiguring the tile`() {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = 9L)
        val actions = stateWith(
            target,
            raTile = RaTileStatus(signedIn = true, tracksGame = true, browseCount = 3)
        ).menuActions

        assertEquals(CustomTileMenuAction.BROWSE_ACHIEVEMENTS, actions.first())
        assertTrue(
            actions.indexOf(CustomTileMenuAction.BROWSE_ACHIEVEMENTS) <
                actions.indexOf(CustomTileMenuAction.EDIT_TILE)
        )
    }

    @Test
    fun `a retroachievements tile with nothing to browse offers only the change row`() {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)
        val actions = stateWith(target, raTile = RaTileStatus(signedIn = true)).menuActions

        assertTrue(CustomTileMenuAction.BROWSE_ACHIEVEMENTS !in actions)
        assertTrue(CustomTileMenuAction.EDIT_TILE in actions)
    }

    @Test
    fun `a surface that cannot run the setup offers no change row`() {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)
        val actions = stateWith(target, supportsRaTileSetup = false).menuActions

        assertTrue(CustomTileMenuAction.EDIT_TILE !in actions)
    }

    @Test
    fun `a folder offers its appearance right after renaming it`() {
        val actions = stateWith(HomeTileTargetRef.Folder(name = "RPGs")).menuActions

        assertEquals(
            actions.indexOf(CustomTileMenuAction.RENAME_FOLDER) + 1,
            actions.indexOf(CustomTileMenuAction.FOLDER_APPEARANCE)
        )
    }

    @Test
    fun `appearance is never offered on a tile that is not a folder`() {
        assertTrue(CustomTileMenuAction.FOLDER_APPEARANCE !in stateWith(game).menuActions)
    }

    @Test
    fun `a library link can be refiltered after it is placed`() {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK)

        assertTrue(CustomTileMenuAction.EDIT_FILTERS in stateWith(target).menuActions)
    }

    /**
     * A press on a library link opens the library. Reading the play wording off "not the
     * RetroAchievements tile" promised a launch the press does not perform.
     */
    @Test
    fun `a library link offers to open, not to play`() {
        val link = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK)
        val random = HomeTileTargetRef.Feature(FeatureTileKind.RANDOM_GAME)

        assertEquals(
            com.nendo.argosy.R.string.ui_custom_grid_confirm_open,
            stateWith(link).confirmLabelRes
        )
        assertEquals(
            com.nendo.argosy.R.string.ui_custom_grid_confirm_play,
            stateWith(random).confirmLabelRes
        )
    }

    @Test
    fun `a collection playing through offers finishing before choosing what is next`() {
        val target = HomeTileTargetRef.Collection(collectionId = 3L, focusGameId = 11L)
        val actions = stateWith(target).menuActions

        assertEquals(CustomTileMenuAction.ADVANCE_FOCUS_GAME, actions.first())
        assertEquals(CustomTileMenuAction.SET_FOCUS_GAME, actions[1])
    }

    @Test
    fun `a collection and a library link offer a cover picture but no default cover until one is set`() {
        val targets = listOf(
            HomeTileTargetRef.Collection(collectionId = 3L),
            HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK)
        )

        targets.forEach { target ->
            val actions = stateWith(target).menuActions
            assertTrue(CustomTileMenuAction.COVER_PICTURE in actions)
            assertTrue(CustomTileMenuAction.DEFAULT_COVER !in actions)
        }
    }

    @Test
    fun `a tile with a cover picture offers to return to the default right after choosing another`() {
        val picture = TileImageSource.File("/p.png")
        val targets = listOf(
            HomeTileTargetRef.Collection(collectionId = 3L, coverSource = picture),
            HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK, coverSource = picture)
        )

        targets.forEach { target ->
            val actions = stateWith(target).menuActions
            assertEquals(
                actions.indexOf(CustomTileMenuAction.COVER_PICTURE) + 1,
                actions.indexOf(CustomTileMenuAction.DEFAULT_COVER)
            )
        }
    }

    @Test
    fun `a collection with a cover picture can choose how it sits in the cell`() {
        val target = HomeTileTargetRef.Collection(collectionId = 3L, coverSource = TileImageSource.File("/p.png"))

        assertTrue(CustomTileMenuAction.FIT_COVER in stateWith(target).menuActions)
        assertTrue(CustomTileMenuAction.FIT_COVER !in stateWith(HomeTileTargetRef.Collection(3L)).menuActions)
    }

    @Test
    fun `no other tile offers a cover picture`() {
        val others = listOf(
            game,
            HomeTileTargetRef.Feature(FeatureTileKind.RANDOM_GAME),
            HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, coverSource = TileImageSource.File("/p.png")),
            HomeTileTargetRef.Folder(name = "RPGs")
        )

        others.forEach { target ->
            val actions = stateWith(target).menuActions
            assertTrue(CustomTileMenuAction.COVER_PICTURE !in actions)
            assertTrue(CustomTileMenuAction.DEFAULT_COVER !in actions)
        }
    }

    @Test
    fun `the danger fence opens at removing the tile, not at deleting the page`() {
        val actions = stateWith(game).menuActions
        val from = stateWith(game).menuDangerFromIndex

        assertEquals(actions.indexOf(CustomTileMenuAction.REMOVE), from)
        assertTrue(actions.drop(from!!).all { it.isDestructive })
    }

    @Test
    fun `the fence stays contiguous whichever destructive rows the menu reaches`() {
        val onlyPage = stateWith(target = null).menuActions
        val onlyPageFrom = stateWith(target = null).menuDangerFromIndex
        assertEquals(onlyPage.indexOf(CustomTileMenuAction.DELETE_PAGE), onlyPageFrom)

        val onlyTile = stateWith(game, pages = 1)
        assertTrue(CustomTileMenuAction.DELETE_PAGE !in onlyTile.menuActions)
        assertEquals(
            onlyTile.menuActions.indexOf(CustomTileMenuAction.REMOVE),
            onlyTile.menuDangerFromIndex
        )
        assertTrue(onlyTile.menuActions.drop(onlyTile.menuDangerFromIndex!!).all { it.isDestructive })
    }

    private fun stateWithHidden(): CustomGridState = CustomGridState(
        tiles = listOf(
            tile(game),
            HomeTile(id = 2L, pageIndex = 0, rect = TileRect(6, 0), target = HomeTileTargetRef.Game(8L))
        ),
        page = 0,
        cell = GridCell(0, 0),
        columns = 4,
        rows = 4,
        storedPages = 2
    )

    @Test
    fun `hidden tiles lead the page rows, ahead of the destructive fence`() {
        val actions = stateWithHidden().menuActions

        assertEquals(
            listOf(
                CustomTileMenuAction.FIT_COVER,
                CustomTileMenuAction.ARRANGE,
                CustomTileMenuAction.MOVE_TO_FOLDER,
                CustomTileMenuAction.HIDDEN_TILES,
                CustomTileMenuAction.PAGE_BACKDROP,
                CustomTileMenuAction.PAGE_MUSIC,
                CustomTileMenuAction.REMOVE,
                CustomTileMenuAction.DELETE_PAGE
            ),
            actions
        )
        assertEquals(actions.indexOf(CustomTileMenuAction.REMOVE), stateWithHidden().menuDangerFromIndex)
    }

    @Test
    fun `hidden tiles are offered on an empty cell too`() {
        val state = stateWithHidden().copy(cell = GridCell(2, 2))

        assertTrue(CustomTileMenuAction.HIDDEN_TILES in state.menuActions)
    }

    @Test
    fun `a page with nothing hidden offers no hidden tiles row`() {
        assertTrue(CustomTileMenuAction.HIDDEN_TILES !in stateWith(game).menuActions)
    }

    @Test
    fun `the hidden list counts and fences on the hidden tiles alone`() {
        val state = stateWithHidden().copy(showMenu = true, menuListsHidden = true)

        assertEquals(listOf(2L), state.hiddenTiles.map { it.id })
        assertEquals(1, state.menuEntryCount)
        assertNull(state.menuDangerFromIndex)
    }

    @Test
    fun `a hidden tile is never drawn or focused`() {
        val state = stateWithHidden()

        assertEquals(listOf(1L), state.tilesOnPage(0).map { it.id })
        assertNull(state.tileAt(GridCell(3, 0)))
    }

    @Test
    fun `an empty cell on the only page has nothing to destroy`() {
        val state = stateWith(target = null, pages = 1)

        assertTrue(CustomTileMenuAction.REMOVE !in state.menuActions)
        assertTrue(CustomTileMenuAction.DELETE_PAGE !in state.menuActions)
        assertNull(state.menuDangerFromIndex)
    }
}

private val CustomTileMenuAction.isDestructive: Boolean
    get() = this == CustomTileMenuAction.REMOVE || this == CustomTileMenuAction.DELETE_PAGE
