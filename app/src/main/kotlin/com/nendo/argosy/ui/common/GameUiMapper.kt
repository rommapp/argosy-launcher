package com.nendo.argosy.ui.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L

private suspend fun GameEntity.resolveDownloaded(
    downloadStatus: DownloadFileStatusRepository
): Boolean = when {
    source == GameSource.ANDROID_APP -> true
    steamAppId != null && isExternallyManaged -> true
    steamAppId != null && localPath != null ->
        downloadStatus.isDownloadComplete(localPath)
    else -> localPath != null
}

private suspend fun GameListItem.resolveDownloaded(
    downloadStatus: DownloadFileStatusRepository
): Boolean = when {
    source == GameSource.ANDROID_APP -> true
    steamAppId != null && isExternallyManaged -> true
    steamAppId != null && localPath != null ->
        downloadStatus.isDownloadComplete(localPath)
    else -> localPath != null
}

suspend fun GameEntity.toHomeGameUi(
    downloadStatus: DownloadFileStatusRepository,
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    newThreshold: Instant = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
): HomeGameUi {
    val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
    val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
    val downloaded = resolveDownloaded(downloadStatus)
    return HomeGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformDisplayName = platformDisplayName ?: platformSlug,
        coverPath = coverPath,
        gradientColors = gradientColors,
        backgroundPath = effectiveBackground,
        boxBackPath = boxBackPath?.takeIf { it.startsWith("/") },
        boxSpinePath = boxSpinePath?.takeIf { it.startsWith("/") },
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
        titleId = displayTitleId
    )
}

suspend fun GameEntity.toLibraryGameUi(
    downloadStatus: DownloadFileStatusRepository,
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
    isDownloaded = resolveDownloaded(downloadStatus),
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden
)

suspend fun GameListItem.toHomeGameUi(
    downloadStatus: DownloadFileStatusRepository,
    platformDisplayName: String? = null,
    newThreshold: Instant = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
): HomeGameUi {
    val downloaded = resolveDownloaded(downloadStatus)
    return HomeGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformDisplayName = platformDisplayName ?: platformSlug,
        coverPath = coverPath,
        backgroundPath = coverPath,
        developer = null,
        releaseYear = releaseYear,
        genre = genre,
        isFavorite = isFavorite,
        isDownloaded = downloaded,
        isRommGame = rommId != null,
        isSteamGame = steamAppId != null,
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        needsInstall = needsAndroidInstall,
        isNew = addedAt.isAfter(newThreshold) && lastPlayed == null,
        sortTitle = sortTitle,
        gameModes = gameModes,
        addedAt = addedAt.toEpochMilli(),
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        lastPlayedAt = lastPlayed?.toEpochMilli(),
        isPlayable = downloaded
    )
}

suspend fun GameListItem.toLibraryGameUi(
    downloadStatus: DownloadFileStatusRepository,
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
    isDownloaded = resolveDownloaded(downloadStatus),
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden
)
