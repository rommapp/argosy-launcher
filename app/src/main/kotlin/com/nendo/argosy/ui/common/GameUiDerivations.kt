package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import java.io.File

private const val ANDROID_PLATFORM_SLUG = "android"
private const val STEAM_DOWNLOAD_COMPLETE_MARKER = ".download_complete"

val GameEntity.isAndroidApp: Boolean
    get() = source == GameSource.ANDROID_APP || platformSlug == ANDROID_PLATFORM_SLUG

val GameListItem.isAndroidApp: Boolean
    get() = source == GameSource.ANDROID_APP || platformSlug == ANDROID_PLATFORM_SLUG

val GameEntity.isSteamGame: Boolean
    get() = source == GameSource.STEAM || steamAppId != null

val GameEntity.isRommGame: Boolean
    get() = rommId != null || source == GameSource.STEAM

val GameListItem.isRommGame: Boolean
    get() = rommId != null || source == GameSource.STEAM

val GameEntity.needsAndroidInstall: Boolean
    get() = platformSlug == ANDROID_PLATFORM_SLUG &&
        localPath != null &&
        packageName == null &&
        source != GameSource.ANDROID_APP

val GameListItem.needsAndroidInstall: Boolean
    get() = platformSlug == ANDROID_PLATFORM_SLUG &&
        localPath != null &&
        packageName == null &&
        source != GameSource.ANDROID_APP

val GameEntity.isPlayableOrInstalled: Boolean
    get() = when {
        source == GameSource.ANDROID_APP -> true
        steamAppId != null && isExternallyManaged -> true
        steamAppId != null && localPath != null ->
            File(localPath, STEAM_DOWNLOAD_COMPLETE_MARKER).exists()
        else -> localPath != null
    }

val GameListItem.isPlayableOrInstalled: Boolean
    get() = when {
        source == GameSource.ANDROID_APP -> true
        steamAppId != null && isExternallyManaged -> true
        steamAppId != null && localPath != null ->
            File(localPath, STEAM_DOWNLOAD_COMPLETE_MARKER).exists()
        else -> localPath != null
    }
