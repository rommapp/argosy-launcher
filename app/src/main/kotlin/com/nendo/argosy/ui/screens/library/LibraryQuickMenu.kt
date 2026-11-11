package com.nendo.argosy.ui.screens.library

enum class LibraryQuickMenuRow(val isDangerous: Boolean = false) {
    PRIMARY,
    MOVE_TO_DISPLAY,
    FAVORITE,
    DETAILS,
    ADD_TO_COLLECTION,
    ADD_TO_GRID,
    ACTIVE_VARIANT,
    REFRESH,
    RESYNC_PLATFORM,
    DELETE(isDangerous = true),
    REMOVE_FROM_LIBRARY(isDangerous = true),
    HIDE(isDangerous = true),
    CLOSE_GAME(isDangerous = true)
}

/**
 * The quick menu's rows in order. [MOVE_TO_DISPLAY][LibraryQuickMenuRow.MOVE_TO_DISPLAY] appears
 * once per move target, in target order; [moveTargetIndexAt] maps a row back to its target.
 */
fun libraryQuickMenuRows(
    game: LibraryGameUi,
    isCustomGridHome: Boolean,
    hasSiblingGroup: Boolean,
    isRunning: Boolean = false,
    moveTargetCount: Int = 0
): List<LibraryQuickMenuRow> = buildList {
    add(LibraryQuickMenuRow.PRIMARY)
    if (isRunning) repeat(moveTargetCount) { add(LibraryQuickMenuRow.MOVE_TO_DISPLAY) }
    add(LibraryQuickMenuRow.FAVORITE)
    add(LibraryQuickMenuRow.DETAILS)
    add(LibraryQuickMenuRow.ADD_TO_COLLECTION)
    if (isCustomGridHome) add(LibraryQuickMenuRow.ADD_TO_GRID)
    if (hasSiblingGroup) add(LibraryQuickMenuRow.ACTIVE_VARIANT)
    if (game.isRommGame || game.isAndroidApp) add(LibraryQuickMenuRow.REFRESH)
    add(LibraryQuickMenuRow.RESYNC_PLATFORM)
    if (game.isDownloaded || game.needsInstall) add(LibraryQuickMenuRow.DELETE)
    if (game.canRemoveFromLibrary) add(LibraryQuickMenuRow.REMOVE_FROM_LIBRARY)
    add(LibraryQuickMenuRow.HIDE)
    if (isRunning) add(LibraryQuickMenuRow.CLOSE_GAME)
}

fun List<LibraryQuickMenuRow>.moveTargetIndexAt(position: Int): Int =
    take(position).count { it == LibraryQuickMenuRow.MOVE_TO_DISPLAY }
