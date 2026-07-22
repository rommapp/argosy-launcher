package com.nendo.argosy.ui.common

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformDefinitions

private const val ANDROID_PLATFORM_SLUG = "android"

val GameEntity.displayTitleId: String?
    get() = titleId?.takeIf { platformSlug in PlatformDefinitions.TITLE_ID_PLATFORMS }

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
