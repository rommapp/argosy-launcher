package com.nendo.argosy.libretro.slot

import android.os.Bundle

internal object SlotProtocol {
    const val REGISTER = 1
    const val START_SESSION = 2
    const val RESUMED = 3
    const val PAUSED = 4
    const val UPDATE_HARDCORE = 5
    const val SAVE_WRITTEN = 6
    const val CACHE_FOR_QUIT = 7
    const val END_SESSION = 8
    const val SHOW_RESUME = 9

    const val SESSION_STATE = 101
    const val SAVE_WRITTEN_PUSH = 102
    const val CACHE_DONE = 103
    const val SAVE_AND_CLOSE = 104
    const val GUIDE_KEY = 105
    const val NOTICE = 106
    const val RESUME_CLOSED = 107

    const val KEY_SLOT = "slot"
    const val KEY_PID = "pid"
    const val KEY_TASK_ID = "taskId"
    const val KEY_GAME_ID = "gameId"
    const val KEY_VARIANT_FILE_ID = "variantFileId"
    const val KEY_STARTED_AT = "startedAt"
    const val KEY_PLATFORM = "platform"
    const val KEY_HARDCORE = "hardcore"
    const val KEY_CORE_NAME = "coreName"
    const val KEY_NEW_GAME = "newGame"
    const val KEY_NETPLAY_GUEST = "netplayGuest"
    const val KEY_ORIGIN = "origin"
    const val KEY_SKIP_SAVE_SYNC = "skipSaveSync"
    const val KEY_REQUEST_ID = "requestId"
    const val KEY_SESSION_START = "sessionStart"
    const val KEY_PRESSED = "pressed"
    const val KEY_DISPLAY_ID = "displayId"
    const val KEY_SHOWN = "shown"

    const val NONE = -1L

    fun Bundle.optionalLong(key: String): Long? = getLong(key, NONE).takeIf { it != NONE }
}
