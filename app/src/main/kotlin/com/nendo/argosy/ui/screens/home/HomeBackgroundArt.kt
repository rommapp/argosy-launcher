package com.nendo.argosy.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.nendo.argosy.ui.common.rememberResolvedBackgroundPath
import com.nendo.argosy.ui.common.rememberResolvedCoverPath

@Immutable
data class HomeBackgroundArt(val path: String, val isScreenshot: Boolean)

/**
 * The art Home draws behind [game]: its background when one is on disk, else its cover on disk,
 * else whichever of the two remote paths exists.
 */
@Composable
fun rememberHomeBackgroundArt(game: HomeGameUi): HomeBackgroundArt? {
    val background = rememberResolvedBackgroundPath(game.id, game.backgroundPath)
    val cover = rememberResolvedCoverPath(game.id, game.coverPath)
    val chosen = when {
        background?.startsWith("/") == true -> background
        cover?.startsWith("/") == true -> cover
        else -> background ?: cover
    } ?: return null
    return HomeBackgroundArt(chosen, game.backgroundIsScreenshotOrArt && chosen == background)
}
