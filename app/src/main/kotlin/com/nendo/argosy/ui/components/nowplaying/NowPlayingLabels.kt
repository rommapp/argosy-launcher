package com.nendo.argosy.ui.components.nowplaying

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingStyle

@get:StringRes
val NowPlayingStyle.labelRes: Int
    get() = when (this) {
        NowPlayingStyle.RECORD -> R.string.now_playing_style_record
        NowPlayingStyle.CD -> R.string.now_playing_style_cd
        NowPlayingStyle.COVER -> R.string.now_playing_style_cover
    }

@get:StringRes
val NowPlayingLayout.labelRes: Int
    get() = when (this) {
        NowPlayingLayout.SHELF -> R.string.now_playing_layout_shelf
        NowPlayingLayout.SIMPLE -> R.string.now_playing_layout_simple
    }
