package com.nendo.argosy.data.model

/**
 * How the file behind [com.nendo.argosy.data.local.entity.GameEntity.localPath] got onto the
 * device. Stored as the enum name; the token is compared and persisted, never shown.
 *
 * Only a download completion path writes a download origin. Every scan, discovery or repair
 * that links a row to a file it found on disk writes [ADOPTED], and a hard reset leaves
 * [ADOPTED] files where they are because Argosy did not put them there.
 */
enum class FileOrigin {
    ROMM_DOWNLOAD,
    STEAM_DOWNLOAD,
    ADOPTED;

    val deletedOnReset: Boolean get() = this != ADOPTED
}
