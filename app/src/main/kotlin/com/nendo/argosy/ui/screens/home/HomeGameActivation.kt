package com.nendo.argosy.ui.screens.home

enum class HomeGameActivation {
    INSTALL,
    LAUNCH,
    RESUME_DOWNLOAD,
    STEAM_LINK_LAUNCHER,
    STREAM,
    DOWNLOAD_EXACT,
    DOWNLOAD_WITH_CHOICE
}

/**
 * What pressing [game] does. [exactRow] marks a press on a surface bound to this exact rom, such as
 * a curated grid tile, which downloads that rom without offering its sibling group. [leadsWithStream]
 * is the stream-first answer for this game, and a game it covers streams in place of downloading.
 */
fun homeGameActivation(
    game: HomeGameUi,
    indicator: GameDownloadIndicator,
    exactRow: Boolean,
    leadsWithStream: Boolean = false
): HomeGameActivation = when {
    game.needsInstall -> HomeGameActivation.INSTALL
    game.isDownloaded -> HomeGameActivation.LAUNCH
    indicator.isPaused || indicator.isQueued -> HomeGameActivation.RESUME_DOWNLOAD
    game.isSteamGame -> HomeGameActivation.STEAM_LINK_LAUNCHER
    leadsWithStream -> HomeGameActivation.STREAM
    exactRow -> HomeGameActivation.DOWNLOAD_EXACT
    else -> HomeGameActivation.DOWNLOAD_WITH_CHOICE
}

fun HomeGameUi.isOnDevice(indicator: GameDownloadIndicator): Boolean =
    isDownloaded || needsInstall || indicator.isShown
