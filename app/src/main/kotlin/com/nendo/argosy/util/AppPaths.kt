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

    fun romCacheDir(filesDir: File): File = File(filesDir, ROM_CACHE_DIR)

    fun libretroSavesDir(filesDir: File): File = File(filesDir, LIBRETRO_SAVES_SUBDIR)

    fun libretroStatesDir(filesDir: File): File = File(filesDir, LIBRETRO_STATES_SUBDIR)

    fun libretroSystemDir(filesDir: File): File = File(filesDir, LIBRETRO_SYSTEM_SUBDIR)
}
