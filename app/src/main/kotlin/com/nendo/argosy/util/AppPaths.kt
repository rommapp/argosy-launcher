package com.nendo.argosy.util

import java.io.File

object AppPaths {

    const val STEAM_STAGING_DIR = "steam_staging"

    const val SAVE_CACHE_DIR = "save_cache"

    const val STATE_CACHE_DIR = "state_cache"

    const val ROM_CACHE_DIR = "rom_cache"

    const val LIBRETRO_SAVES_SUBDIR = "libretro/saves"

    const val LIBRETRO_STATES_SUBDIR = "libretro/states"

    const val LIBRETRO_SYSTEM_SUBDIR = "libretro/system"

    const val FAN_BASE_PATH = "/sys/class/gpio5_pwm2"

    const val FAN_SPEED_PATH = "$FAN_BASE_PATH/speed"

    const val FAN_STATE_PATH = "$FAN_BASE_PATH/state"

    const val FAN_DUTY_PATH = "$FAN_BASE_PATH/duty"

    fun steamStagingRoot(filesDir: File): File = File(filesDir, STEAM_STAGING_DIR)

    fun steamStagingDir(filesDir: File, appId: Long): File =
        File(filesDir, "$STEAM_STAGING_DIR/$appId")

    fun saveCacheDir(filesDir: File): File = File(filesDir, SAVE_CACHE_DIR)

    fun stateCacheDir(filesDir: File): File = File(filesDir, STATE_CACHE_DIR)

    /**
     * Cache entries are stored under the owning account so two accounts holding a copy of the
     * same game's save never collide on one directory. Rows written before accounts existed
     * carry no owner and keep their unprefixed layout, which is why this returns a relative
     * segment rather than rewriting the stored path.
     *
     * The `u` prefix is load-bearing: the legacy layout puts a bare numeric game id at the same
     * level, and a state-cache sweep already deletes numeric top-level directories as stale.
     */
    fun ownerCacheSegment(ownerUserId: Long?): String =
        if (ownerUserId != null) "$OWNER_DIR_PREFIX$ownerUserId/" else ""

    /**
     * The directory holding one account's cache entries under [root], or null for an owner with
     * no partition of its own -- those entries sit directly under the root alongside every other
     * account's, so there is nothing that can be removed without taking the rest with it.
     */
    fun ownerCacheDir(root: File, ownerUserId: Long?): File? =
        ownerCacheSegment(ownerUserId).trimEnd('/').takeIf { it.isNotEmpty() }?.let { File(root, it) }

    fun isOwnerCacheDir(name: String): Boolean =
        name.startsWith(OWNER_DIR_PREFIX) &&
            name.removePrefix(OWNER_DIR_PREFIX).toLongOrNull() != null

    private const val OWNER_DIR_PREFIX = "u"

    fun romCacheDir(filesDir: File): File = File(filesDir, ROM_CACHE_DIR)

    fun libretroSavesDir(filesDir: File): File = File(filesDir, LIBRETRO_SAVES_SUBDIR)

    fun libretroStatesDir(filesDir: File): File = File(filesDir, LIBRETRO_STATES_SUBDIR)

    fun libretroSystemDir(filesDir: File): File = File(filesDir, LIBRETRO_SYSTEM_SUBDIR)
}
