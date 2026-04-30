package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.steam.resolveSteamGenres
import com.nendo.argosy.ui.common.isAndroidApp
import com.nendo.argosy.ui.common.isRommGame
import com.nendo.argosy.ui.common.isSteamGame

fun GameEntity.toGameDetailUi(
    platformName: String,
    emulatorName: String?,
    canPlay: Boolean,
    isRetroArch: Boolean = false,
    isBuiltIn: Boolean = false,
    hasMultipleCores: Boolean = false,
    selectedCoreName: String? = null,
    achievements: List<AchievementUi> = emptyList(),
    canManageSaves: Boolean = false,
    steamLauncherName: String? = null
): GameDetailUi {
    val remoteUrls = screenshotPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val cachedPaths = cachedScreenshotPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val screenshots = remoteUrls.mapIndexed { index, url ->
        ScreenshotPair(
            remoteUrl = url,
            cachedPath = cachedPaths.getOrNull(index)
        )
    }
    val effectiveBackground = backgroundPath ?: remoteUrls.firstOrNull()
    return GameDetailUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformName = platformName,
        coverPath = coverPath,
        backgroundPath = effectiveBackground,
        developer = developer,
        publisher = publisher,
        releaseYear = releaseYear,
        genre = if (source == GameSource.STEAM) resolveSteamGenres(genre) else genre,
        description = description,
        players = players,
        rating = rating,
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
        isSteamGame = isSteamGame,
        steamLauncherName = steamLauncherName,
        isExternallyManaged = isExternallyManaged,
        managingLauncherDisplayName = steamLauncher
            ?.takeIf { it != GameEntity.LAUNCHER_UNSPECIFIED }
            ?.let { SteamLaunchers.displayNameForPackage(it) },
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        isHidden = isHidden,
        titleId = titleId,
        igdbId = igdbId,
        steamAppId = steamAppId
    )
}

