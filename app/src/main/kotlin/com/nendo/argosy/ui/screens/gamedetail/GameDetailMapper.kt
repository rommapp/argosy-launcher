package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.steam.resolveSteamGenres
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.isAndroidApp
import com.nendo.argosy.ui.common.isRommGame
import com.nendo.argosy.ui.common.isSteamGame

/**
 * [isHidden] is supplied by the caller: hiding is per account and lives in `user_roms_hidden`,
 * so a bare [GameEntity] cannot answer it.
 */
fun GameEntity.toGameDetailUi(
    art: ResolvedGameArt?,
    screenshotRows: List<GameScreenshotEntity>,
    platformName: String,
    emulatorName: String?,
    canPlay: Boolean,
    isRetroArch: Boolean = false,
    isBuiltIn: Boolean = false,
    hasMultipleCores: Boolean = false,
    selectedCoreName: String? = null,
    achievements: List<AchievementUi> = emptyList(),
    canManageSaves: Boolean = false,
    canManageStates: Boolean = false,
    steamLauncherName: String? = null,
    isHidden: Boolean = false
): GameDetailUi {
    val screenshots = screenshotRows.sortedBy { it.position }.map { row ->
        ScreenshotPair(remoteUrl = row.sourceUrl, cachedPath = row.cachedPath)
    }
    val effectiveBackground = art?.backgroundPath ?: screenshots.firstOrNull()?.remoteUrl
    return GameDetailUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformName = platformName,
        coverPath = art?.coverPath,
        overriddenArtSlots = art?.overriddenSlots.orEmpty(),
        backgroundPath = effectiveBackground,
        boxBackPath = art?.boxBackPath?.takeIf { it.startsWith("/") },
        boxSpinePath = art?.boxSpinePath?.takeIf { it.startsWith("/") },
        developer = developer,
        publisher = publisher,
        releaseYear = releaseYear,
        genre = if (source == GameSource.STEAM) resolveSteamGenres(genre) else genre,
        description = description,
        players = players,
        rating = rating,
        timeToBeatMainSec = timeToBeatMainSec,
        timeToBeatExtraSec = timeToBeatExtraSec,
        timeToBeatCompletionistSec = timeToBeatCompletionistSec,
        userRating = userRating,
        userDifficulty = userDifficulty,
        completion = completion,
        status = status,
        isRommGame = isRommGame,
        isFavorite = isFavorite,
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        screenshots = screenshots,
        achievements = achievements,
        emulatorName = emulatorName,
        canPlay = canPlay,
        isMultiDisc = isMultiDisc,
        lastPlayedDiscId = lastPlayedDiscId,
        isRetroArchEmulator = isRetroArch,
        isBuiltInEmulator = isBuiltIn,
        hasMultipleCores = hasMultipleCores,
        selectedCoreName = selectedCoreName,
        canManageSaves = canManageSaves,
        canManageStates = canManageStates,
        isSteamGame = isSteamGame,
        steamLauncherName = steamLauncherName,
        isExternallyManaged = isExternallyManaged,
        managingLauncherDisplayName = steamLauncher
            ?.takeIf { it != GameEntity.LAUNCHER_UNSPECIFIED }
            ?.let { SteamLaunchers.displayNameForPackage(it) },
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        isHidden = isHidden,
        titleId = displayTitleId,
        igdbId = igdbId,
        steamAppId = steamAppId,
        rommFileName = rommFileName
    )
}
