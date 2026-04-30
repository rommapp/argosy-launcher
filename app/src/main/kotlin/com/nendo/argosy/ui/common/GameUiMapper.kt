package com.nendo.argosy.ui.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L

fun GameEntity.toHomeGameUi(
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    newThreshold: Instant = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
): HomeGameUi {
    val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
    val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
    val downloaded = isPlayableOrInstalled
    return HomeGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformDisplayName = platformDisplayName ?: platformSlug,
        coverPath = coverPath,
        gradientColors = gradientColors,
        backgroundPath = effectiveBackground,
        developer = developer,
        releaseYear = releaseYear,
        genre = genre,
        isFavorite = isFavorite,
        isDownloaded = downloaded,
        isRommGame = isRommGame,
        isSteamGame = isSteamGame,
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        achievementCount = achievementCount,
        earnedAchievementCount = earnedAchievementCount,
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        needsInstall = needsAndroidInstall,
        youtubeVideoId = youtubeVideoId,
        isNew = addedAt.isAfter(newThreshold) && lastPlayed == null,
        sortTitle = sortTitle,
        gameModes = gameModes,
        franchises = franchises,
        addedAt = addedAt.toEpochMilli(),
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        lastPlayedAt = lastPlayed?.toEpochMilli(),
        isPlayable = downloaded,
        description = description,
        status = status,
        titleId = titleId
    )
}

fun GameEntity.toLibraryGameUi(
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    emulatorName: String? = null
): LibraryGameUi = LibraryGameUi(
    id = id,
    title = title,
    sortTitle = sortTitle,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformDisplayName ?: platformSlug,
    coverPath = coverPath,
    gradientColors = gradientColors,
    source = source,
    isFavorite = isFavorite,
    isDownloaded = isPlayableOrInstalled,
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden
)

fun GameListItem.toLibraryGameUi(
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    emulatorName: String? = null
): LibraryGameUi = LibraryGameUi(
    id = id,
    title = title,
    sortTitle = sortTitle,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformDisplayName ?: platformSlug,
    coverPath = coverPath,
    gradientColors = gradientColors,
    source = source,
    isFavorite = isFavorite,
    isDownloaded = isPlayableOrInstalled,
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden
)
