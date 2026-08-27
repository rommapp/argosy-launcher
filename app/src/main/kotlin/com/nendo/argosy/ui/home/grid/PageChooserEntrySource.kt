package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.components.PageChooserAction
import com.nendo.argosy.ui.components.PageChooserEntry
import com.nendo.argosy.ui.components.PageChooserKind
import com.nendo.argosy.ui.components.PageChooserState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val ART_SEARCH_LIMIT = 60

/**
 * The rows the page chooser offers, built once for every home surface.
 *
 * The question the chooser asks does not change with the display it is shown on, so the answers do
 * not either. Built per surface they drifted: one screen offered a game its artwork and the other
 * offered nothing at all.
 */
@Singleton
class PageChooserEntrySource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val bgmPlaylistRepository: BgmPlaylistRepository
) {

    /**
     * [canBrowseFiles] answers whether the surface can open the file browser. A companion display
     * cannot yet, so it offers the sources it can reach rather than a row that leads nowhere.
     */
    suspend fun entriesFor(
        chooser: PageChooserState,
        focusedCollection: HomeTileTargetRef.Collection?,
        canBrowseFiles: Boolean = true
    ): List<PageChooserEntry> = when {
        chooser.kind == PageChooserKind.FOCUS_GAME -> focusGameEntries(focusedCollection)
        chooser.kind == PageChooserKind.MUSIC -> musicEntries()
        chooser.gameId != null -> gameArtEntries(chooser.gameId)
        chooser.gameTitle != null -> gameArtSourceEntries(chooser.query)
        else -> backdropRootEntries(canBrowseFiles)
    }

    private suspend fun focusGameEntries(
        collection: HomeTileTargetRef.Collection?
    ): List<PageChooserEntry> {
        if (collection == null) return emptyList()
        return collectionRepository.getGamesInCollection(collection.collectionId).map { game ->
            PageChooserEntry(
                label = game.title,
                subtitle = if (game.id == collection.focusGameId) {
                    context.getString(R.string.ui_page_chooser_focus_game_current)
                } else {
                    null
                },
                previewPath = game.coverPath,
                action = PageChooserAction.UseFocusGame(game.id)
            )
        }
    }

    private fun backdropRootEntries(canBrowseFiles: Boolean): List<PageChooserEntry> = buildList {
        add(
            PageChooserEntry(
                label = context.getString(R.string.ui_page_chooser_source_game_art),
                subtitle = context.getString(R.string.ui_page_chooser_source_game_art_subtitle),
                action = PageChooserAction.BrowseGameArt
            )
        )
        if (canBrowseFiles) {
            add(
                PageChooserEntry(
                    label = context.getString(R.string.ui_page_chooser_source_file),
                    subtitle = context.getString(R.string.ui_page_chooser_source_file_subtitle),
                    action = PageChooserAction.OpenFileBrowser
                )
            )
        }
        add(
            PageChooserEntry(
                label = context.getString(R.string.ui_page_chooser_source_none),
                subtitle = context.getString(R.string.ui_page_chooser_source_none_subtitle),
                action = PageChooserAction.ClearBackdrop
            )
        )
    }

    private suspend fun gameArtSourceEntries(query: String): List<PageChooserEntry> {
        val matches = gameRepository.searchForQuickMenu(query.trim(), ART_SEARCH_LIMIT).first()
        val platformNames = platformRepository.getAllPlatforms().associate { it.id to it.name }
        return matches.filter { artworkOf(it).isNotEmpty() }.map { game ->
            PageChooserEntry(
                label = game.title,
                subtitle = platformNames[game.platformId].orEmpty(),
                previewPath = game.coverPath,
                action = PageChooserAction.OpenGameArt(gameId = game.id, title = game.title)
            )
        }
    }

    private suspend fun gameArtEntries(gameId: Long): List<PageChooserEntry> {
        val game = gameRepository.getById(gameId) ?: return emptyList()
        return artworkOf(game).map { art ->
            PageChooserEntry(
                label = art.label,
                previewPath = art.path,
                action = PageChooserAction.UseArt(art.path)
            )
        }
    }

    private fun artworkOf(game: GameEntity): List<PageArtwork> = buildList {
        game.backgroundPath?.takeIf { it.startsWith("/") }?.let {
            add(PageArtwork(context.getString(R.string.ui_page_chooser_art_background), it))
        }
        game.coverPath?.takeIf { it.startsWith("/") }?.let {
            add(PageArtwork(context.getString(R.string.ui_page_chooser_art_cover), it))
        }
        game.cachedScreenshotPaths
            ?.split(",")
            ?.filter { it.isNotBlank() && it.startsWith("/") }
            ?.forEachIndexed { index, path ->
                val number: Int = index + 1
                val label = context.getString(R.string.ui_page_chooser_art_screenshot, number)
                add(PageArtwork(label, path))
            }
    }.distinctBy { it.path }

    private suspend fun musicEntries(): List<PageChooserEntry> = buildList {
        add(
            PageChooserEntry(
                label = context.getString(R.string.ui_page_chooser_music_launcher),
                action = PageChooserAction.UseLauncherMusic
            )
        )
        add(
            PageChooserEntry(
                label = context.getString(R.string.ui_page_chooser_music_tile_audio),
                action = PageChooserAction.UseTileAudio
            )
        )
        val tracks = bgmPlaylistRepository.playableTracks()
        if (tracks.isEmpty()) return@buildList
        add(
            PageChooserEntry(
                label = context.getString(R.string.ui_page_chooser_music_soundtracks_header),
                isHeader = true
            )
        )
        tracks.forEach { track ->
            add(
                PageChooserEntry(
                    label = track.displayName,
                    action = PageChooserAction.UseTrack(track.filePath)
                )
            )
        }
    }
}

private data class PageArtwork(val label: String, val path: String)
