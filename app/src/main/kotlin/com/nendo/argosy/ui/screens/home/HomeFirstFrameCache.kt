package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.ui.components.GridPageSettings
import com.nendo.argosy.ui.components.RaTileStatus
import com.nendo.argosy.ui.components.TileCollectionUi
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import javax.inject.Inject
import javax.inject.Singleton

data class HomeTilesSnapshot(
    val gridKind: HomeGridKind,
    val tiles: List<HomeTile>,
    val raTile: RaTileStatus,
    val tileGames: Map<Long, HomeGameUi>,
    val tileCollections: Map<Long, TileCollectionUi>,
    val tileApps: Map<String, String>,
    val tileLibraryLinks: Map<Long, LibraryLinkTileUi>,
    val tileFolders: Map<Long, FolderTileUi>,
    val tileShowcases: Map<Long, PresentationSlot.PlatformShowcase>,
    val continueGameId: Long?,
    val raTileSummary: RaTileContent?
)

data class HomeFocusSnapshot(
    val row: HomeRow,
    val gameIndex: Int,
    val gridPage: Int,
    val gridCell: GridCell
)

@Singleton
class HomeFirstFrameCache @Inject constructor() {
    @Volatile
    var tiles: HomeTilesSnapshot? = null

    @Volatile
    var pageSettings: Map<Int, GridPageSettings>? = null

    @Volatile
    var focus: HomeFocusSnapshot? = null
}
