package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.storage.GameStorageLocation

@get:StringRes
val GameStorageLocation.labelRes: Int
    get() = when (this) {
        GameStorageLocation.INTERNAL -> R.string.settings_storage_location_internal
        GameStorageLocation.REMOVABLE -> R.string.settings_storage_location_removable
        GameStorageLocation.MIXED -> R.string.settings_storage_location_mixed
        GameStorageLocation.UNKNOWN -> R.string.settings_storage_location_unknown
    }

/**
 * Storage-card label naming where a game came from, or null for sources the card leaves
 * unlabelled.
 */
@get:StringRes
val GameSource.storageSourceLabelRes: Int?
    get() = when (this) {
        GameSource.ROMM_REMOTE, GameSource.ROMM_SYNCED -> R.string.settings_storage_game_source_romm
        GameSource.LOCAL_ONLY -> R.string.settings_storage_game_source_local_only
        GameSource.STEAM, GameSource.ANDROID_APP, GameSource.GAMENATIVE -> null
    }
