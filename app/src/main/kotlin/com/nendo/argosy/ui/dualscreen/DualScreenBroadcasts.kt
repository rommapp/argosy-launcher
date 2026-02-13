/**
 * DUAL-SCREEN COMPONENT - Broadcast action constants for cross-process communication.
 */
package com.nendo.argosy.ui.dualscreen

object DualScreenBroadcasts {
    const val ACTION_GAME_SELECTED = "com.nendo.argosy.DUAL_GAME_SELECTED"
    const val ACTION_REFOCUS_LOWER = "com.nendo.argosy.DUAL_REFOCUS_LOWER"
    const val ACTION_KEY_EVENT = "com.nendo.argosy.DUAL_KEY_EVENT"
    const val ACTION_OPEN_START_MENU = "com.nendo.argosy.DUAL_OPEN_START_MENU"
    const val ACTION_CLOSE_START_MENU = "com.nendo.argosy.DUAL_CLOSE_START_MENU"
    const val ACTION_GAME_DETAIL_OPENED = "com.nendo.argosy.DUAL_GAME_DETAIL_OPENED"
    const val ACTION_GAME_DETAIL_CLOSED = "com.nendo.argosy.DUAL_GAME_DETAIL_CLOSED"
    const val ACTION_SCREENSHOT_SELECTED = "com.nendo.argosy.DUAL_SCREENSHOT_SELECTED"
    const val ACTION_SCREENSHOT_CLEAR = "com.nendo.argosy.DUAL_SCREENSHOT_CLEAR"

    const val EXTRA_KEY_CODE = "key_code"
    const val EXTRA_KEY_ACTION = "key_action"
    const val EXTRA_GAME_ID = "game_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_COVER_PATH = "cover_path"
    const val EXTRA_BACKGROUND_PATH = "background_path"
    const val EXTRA_PLATFORM_NAME = "platform_name"
    const val EXTRA_PLATFORM_SLUG = "platform_slug"
    const val EXTRA_PLAY_TIME_MINUTES = "play_time_minutes"
    const val EXTRA_LAST_PLAYED_AT = "last_played_at"
    const val EXTRA_STATUS = "status"
    const val EXTRA_RATING = "rating"
    const val EXTRA_DESCRIPTION = "description"
    const val EXTRA_DEVELOPER = "developer"
    const val EXTRA_RELEASE_YEAR = "release_year"
    const val EXTRA_TITLE_ID = "title_id"
    const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
    const val EXTRA_SCREENSHOT_INDEX = "screenshot_index"

    const val ACTION_MODAL_OPEN = "com.nendo.argosy.DUAL_MODAL_OPEN"
    const val ACTION_MODAL_RESULT = "com.nendo.argosy.DUAL_MODAL_RESULT"
    const val EXTRA_MODAL_TYPE = "modal_type"
    const val EXTRA_MODAL_VALUE = "modal_value"
    const val EXTRA_MODAL_STATUS_SELECTED = "modal_status_selected"
    const val EXTRA_MODAL_STATUS_CURRENT = "modal_status_current"
    const val EXTRA_MODAL_DISMISSED = "modal_dismissed"

    const val EXTRA_EMULATOR_NAMES = "emulator_names"
    const val EXTRA_EMULATOR_VERSIONS = "emulator_versions"
    const val EXTRA_EMULATOR_CURRENT = "emulator_current"
    const val EXTRA_SELECTED_INDEX = "selected_index"

    const val EXTRA_COLLECTION_IDS = "collection_ids"
    const val EXTRA_COLLECTION_NAMES = "collection_names"
    const val EXTRA_COLLECTION_CHECKED = "collection_checked"
    const val EXTRA_COLLECTION_TOGGLE_ID = "collection_toggle_id"
    const val EXTRA_COLLECTION_CREATE_NAME = "collection_create_name"

    const val ACTION_INLINE_UPDATE = "com.nendo.argosy.DUAL_INLINE_UPDATE"
    const val EXTRA_INLINE_FIELD = "inline_field"
    const val EXTRA_INLINE_INT_VALUE = "inline_int_value"
    const val EXTRA_INLINE_STRING_VALUE = "inline_string_value"

    const val ACTION_DIRECT_ACTION = "com.nendo.argosy.DUAL_DIRECT_ACTION"
    const val EXTRA_ACTION_TYPE = "action_type"

    const val ACTION_COMPANION_RESUMED = "com.nendo.argosy.DUAL_COMPANION_RESUMED"
    const val ACTION_COMPANION_PAUSED = "com.nendo.argosy.DUAL_COMPANION_PAUSED"
}
