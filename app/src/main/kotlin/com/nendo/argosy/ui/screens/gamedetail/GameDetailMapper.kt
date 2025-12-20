package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource

fun GameEntity.toGameDetailUi(
    platformName: String,
    emulatorName: String?,
    canPlay: Boolean,
    isRetroArch: Boolean = false,
    selectedCoreName: String? = null,
    achievements: List<AchievementUi> = emptyList(),
    canManageSaves: Boolean = false
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
        genre = genre,
        description = description,
        players = players,
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        isRommGame = rommId != null || source == GameSource.STEAM,
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
        selectedCoreName = selectedCoreName,
        canManageSaves = canManageSaves
    )
}

fun AchievementEntity.toAchievementUi() = AchievementUi(
    raId = raId,
    title = title,
    description = description,
    points = points,
    type = type,
    badgeUrl = if (isUnlocked) {
        cachedBadgeUrl ?: badgeUrl
    } else {
        cachedBadgeUrlLock ?: badgeUrlLock ?: cachedBadgeUrl ?: badgeUrl
    },
    isUnlocked = isUnlocked
)
