package com.nendo.argosy.ui.screens.home

enum class HomeGamePrimaryAction { RESUME_GAME, INSTALL, PLAY, RESUME_DOWNLOAD, STREAM, DOWNLOAD }

fun homeGamePrimaryAction(
    game: HomeGameUi,
    isRunning: Boolean,
    indicator: GameDownloadIndicator,
    streamLeads: Boolean
): HomeGamePrimaryAction = when {
    isRunning -> HomeGamePrimaryAction.RESUME_GAME
    game.needsInstall -> HomeGamePrimaryAction.INSTALL
    game.isDownloaded -> HomeGamePrimaryAction.PLAY
    indicator.isPaused -> HomeGamePrimaryAction.RESUME_DOWNLOAD
    streamLeads -> HomeGamePrimaryAction.STREAM
    else -> HomeGamePrimaryAction.DOWNLOAD
}
