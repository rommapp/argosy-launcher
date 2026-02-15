/**
 * DUAL-SCREEN COMPONENT - Broadcast action constants for cross-process communication.
 */
package com.nendo.argosy.ui.dualscreen

object DualScreenBroadcasts {
    const val ACTION_GAME_SELECTED = "com.nendo.argosy.DUAL_GAME_SELECTED"
    const val ACTION_REFOCUS_LOWER = "com.nendo.argosy.DUAL_REFOCUS_LOWER"
    const val ACTION_REFOCUS_UPPER = "com.nendo.argosy.DUAL_REFOCUS_UPPER"
    const val ACTION_OPEN_OVERLAY = "com.nendo.argosy.DUAL_OPEN_OVERLAY"
    const val ACTION_CLOSE_OVERLAY = "com.nendo.argosy.DUAL_CLOSE_OVERLAY"
    const val EXTRA_EVENT_NAME = "event_name"
    const val ACTION_GAME_DETAIL_OPENED = "com.nendo.argosy.DUAL_GAME_DETAIL_OPENED"
    const val ACTION_GAME_DETAIL_CLOSED = "com.nendo.argosy.DUAL_GAME_DETAIL_CLOSED"
    const val ACTION_SCREENSHOT_SELECTED = "com.nendo.argosy.DUAL_SCREENSHOT_SELECTED"
    const val ACTION_SCREENSHOT_CLEAR = "com.nendo.argosy.DUAL_SCREENSHOT_CLEAR"

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
    const val EXTRA_CHANNEL_NAME = "channel_name"
    const val EXTRA_SAVE_CACHE_ID = "save_cache_id"
    const val EXTRA_SAVE_TIMESTAMP = "save_timestamp"
    const val EXTRA_RENAME_TEXT = "rename_text"

    const val ACTION_SAVE_DATA = "com.nendo.argosy.DUAL_SAVE_DATA"
    const val EXTRA_SAVE_DATA_JSON = "save_data_json"
    const val EXTRA_ACTIVE_CHANNEL = "active_channel"
    const val EXTRA_ACTIVE_SAVE_TIMESTAMP = "active_save_timestamp"

    const val ACTION_BACKGROUND_FORWARD = "com.nendo.argosy.DUAL_BACKGROUND_FORWARD"

    const val ACTION_COMPANION_RESUMED = "com.nendo.argosy.DUAL_COMPANION_RESUMED"
    const val ACTION_COMPANION_PAUSED = "com.nendo.argosy.DUAL_COMPANION_PAUSED"

    const val ACTION_VIEW_MODE_CHANGED = "com.nendo.argosy.DUAL_VIEW_MODE_CHANGED"
    const val EXTRA_VIEW_MODE = "view_mode"
    const val EXTRA_IS_APP_BAR_FOCUSED = "is_app_bar_focused"
    const val EXTRA_IS_DRAWER_OPEN = "is_drawer_open"

    const val ACTION_COLLECTION_FOCUSED = "com.nendo.argosy.DUAL_COLLECTION_FOCUSED"
    const val EXTRA_COLLECTION_ID_FOCUSED = "collection_id_focused"
    const val EXTRA_COLLECTION_NAME_DISPLAY = "collection_name_display"
    const val EXTRA_COLLECTION_DESCRIPTION = "collection_description"
    const val EXTRA_COLLECTION_COVER_PATHS = "collection_cover_paths"
    const val EXTRA_COLLECTION_GAME_COUNT = "collection_game_count"
    const val EXTRA_COLLECTION_PLATFORM_SUMMARY = "collection_platform_summary"
    const val EXTRA_COLLECTION_TOTAL_PLAYTIME = "collection_total_playtime"
}
