package com.nendo.argosy.data.launcher

/**
 * The four per-store GameNative Frontend Sync export folders. Slugs and marker extensions are
 * GameNative's own (`frontend_sync_dir_<slug>`, one `<Title><ext>` file per install whose content
 * is the store's numeric id) and are upstream-exact - renaming them breaks marker discovery.
 */
enum class GameNativeSyncFolder(
    val slug: String,
    val displayName: String,
    val markerExtension: String
) {
    STEAM("steam", "Steam", ".steam"),
    GOG("gog", "GOG", ".gog"),
    EPIC("epic", "Epic Games", ".epic"),
    AMAZON("amazon", "Amazon Games", ".amazon")
}
