package com.nendo.argosy.ui.common

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.ResolvedGameArt
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L

internal suspend fun GameEntity.resolveDownloaded(
    downloadStatus: DownloadFileStatusRepository
): Boolean = when {
    source == GameSource.ANDROID_APP -> true
    steamAppId != null -> downloadStatus.isSteamInstalled(isExternallyManaged, localPath)
    else -> localPath != null
}

private suspend fun GameListItem.resolveDownloaded(
    downloadStatus: DownloadFileStatusRepository
): Boolean = when {
    source == GameSource.ANDROID_APP -> true
    steamAppId != null -> downloadStatus.isSteamInstalled(isExternallyManaged, localPath)
    else -> localPath != null
}

suspend fun GameEntity.toHomeGameUi(
    downloadStatus: DownloadFileStatusRepository,
    art: ResolvedGameArt?,
    firstScreenshotUrl: String?,
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    newThreshold: Instant = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
): HomeGameUi {
    val effectiveBackground = art?.backgroundPath ?: firstScreenshotUrl ?: art?.coverPath
    val downloaded = resolveDownloaded(downloadStatus)
    return HomeGameUi(
        id = id,
        title = title,
        platformId = platformId,
        platformSlug = platformSlug,
        platformDisplayName = platformDisplayName ?: platformSlug,
        coverPath = art?.coverPath,
        coverAspectRatio = art?.coverAspectRatio,
        gradientColors = gradientColors,
        backgroundPath = effectiveBackground,
        boxBackPath = art?.boxBackPath?.takeIf { it.startsWith("/") },
        boxSpinePath = art?.boxSpinePath?.takeIf { it.startsWith("/") },
        box3dPath = art?.box3dPath?.takeIf { it.startsWith("/") },
        logoPath = art?.logoPath?.takeIf { it.startsWith("/") },
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
        franchises = franchises,
        addedAt = addedAt.toEpochMilli(),
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        lastPlayedAt = lastPlayed?.toEpochMilli(),
        isPlayable = downloaded,
        description = description,
        status = status,
        titleId = displayTitleId,
        igdbId = igdbId,
        timeToBeatMainSec = timeToBeatMainSec,
        timeToBeatExtraSec = timeToBeatExtraSec,
        timeToBeatCompletionistSec = timeToBeatCompletionistSec,
        players = players
    )
}

/**
 * [isHidden] is supplied by the caller: hiding is per account and lives in `user_roms_hidden`,
 * so a bare [GameEntity] cannot answer it.
 */
suspend fun GameEntity.toLibraryGameUi(
    downloadStatus: DownloadFileStatusRepository,
    art: ResolvedGameArt?,
    platformDisplayName: String? = null,
    gradientColors: Pair<Color, Color>? = null,
    emulatorName: String? = null,
    isHidden: Boolean = false
): LibraryGameUi = LibraryGameUi(
    id = id,
    title = title,
    sortTitle = sortTitle,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformDisplayName ?: platformSlug,
    coverPath = art?.coverPath,
    boxSpinePath = art?.boxSpinePath?.takeIf { it.startsWith("/") },
    box3dPath = art?.box3dPath?.takeIf { it.startsWith("/") },
    gradientColors = gradientColors,
    source = source,
    isFavorite = isFavorite,
    isDownloaded = resolveDownloaded(downloadStatus),
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden,
    listDetails = listDetails
)

suspend fun GameListItem.toHomeGameUi(
    downloadStatus: DownloadFileStatusRepository,
    art: ResolvedGameArt?,
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
        coverPath = art?.coverPath,
        backgroundPath = art?.coverPath,
        developer = developer,
        releaseYear = releaseYear,
        genre = genre,
        isFavorite = isFavorite,
        isDownloaded = downloaded,
        isRommGame = rommId != null,
        isSteamGame = steamAppId != null,
        rating = rating,
        userRating = userRating,
        userDifficulty = userDifficulty,
        achievementCount = achievementCount,
        earnedAchievementCount = earnedAchievementCount,
        isAndroidApp = isAndroidApp,
        packageName = packageName,
        needsInstall = needsAndroidInstall,
        isNew = addedAt.isAfter(newThreshold) && lastPlayed == null,
        isHidden = isHidden,
        status = status,
        igdbId = igdbId,
        timeToBeatMainSec = timeToBeatMainSec,
        sortTitle = sortTitle,
        addedAt = addedAt.toEpochMilli(),
        playCount = playCount,
        playTimeMinutes = playTimeMinutes,
        lastPlayedAt = lastPlayed?.toEpochMilli(),
        isPlayable = downloaded,
        players = players
    )
}

suspend fun GameListItem.toLibraryGameUi(
    downloadStatus: DownloadFileStatusRepository,
    art: ResolvedGameArt?,
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
    coverPath = art?.coverPath,
    boxSpinePath = art?.boxSpinePath?.takeIf { it.startsWith("/") },
    box3dPath = art?.box3dPath?.takeIf { it.startsWith("/") },
    gradientColors = gradientColors,
    source = source,
    isFavorite = isFavorite,
    isDownloaded = resolveDownloaded(downloadStatus),
    isRommGame = isRommGame,
    isAndroidApp = isAndroidApp,
    emulatorName = emulatorName,
    needsInstall = needsAndroidInstall,
    isHidden = isHidden,
    listDetails = listDetails
)
