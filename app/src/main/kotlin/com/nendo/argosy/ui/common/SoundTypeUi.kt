package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType

@get:StringRes
val SoundType.labelRes: Int
    get() = when (this) {
        SoundType.SILENT -> R.string.settings_sounds_type_silent
        SoundType.NAVIGATE -> R.string.settings_sounds_type_navigate
        SoundType.BOUNDARY -> R.string.settings_sounds_type_boundary
        SoundType.SECTION_CHANGE -> R.string.settings_sounds_type_section_change
        SoundType.SELECT -> R.string.settings_sounds_type_select
        SoundType.BACK -> R.string.settings_sounds_type_back
        SoundType.OPEN_MODAL -> R.string.settings_sounds_type_open_modal
        SoundType.CLOSE_MODAL -> R.string.settings_sounds_type_close_modal
        SoundType.FAVORITE -> R.string.settings_sounds_type_favorite
        SoundType.UNFAVORITE -> R.string.settings_sounds_type_unfavorite
        SoundType.DOWNLOAD_START -> R.string.settings_sounds_type_download_start
        SoundType.DOWNLOAD_COMPLETE -> R.string.settings_sounds_type_download_complete
        SoundType.DOWNLOAD_CANCEL -> R.string.settings_sounds_type_download_cancel
        SoundType.ERROR -> R.string.settings_sounds_type_error
        SoundType.VOLUME_PREVIEW -> R.string.settings_sounds_type_volume_preview
        SoundType.TOGGLE -> R.string.settings_sounds_type_toggle
        SoundType.LAUNCH_GAME -> R.string.settings_sounds_type_launch_game
    }
