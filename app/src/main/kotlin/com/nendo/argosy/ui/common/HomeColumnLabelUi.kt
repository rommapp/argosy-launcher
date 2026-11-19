package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeMediaColumnKind

@get:StringRes
val HomeColumnTheme.labelRes: Int
    get() = when (this) {
        HomeColumnTheme.CONTINUE -> R.string.home_columns_theme_continue
        HomeColumnTheme.PLAY_IT_AGAIN -> R.string.home_columns_theme_play_it_again
        HomeColumnTheme.ALMOST_THERE -> R.string.home_columns_theme_almost_there
        HomeColumnTheme.FRIENDS_PLAYING -> R.string.home_columns_theme_friends_playing
        HomeColumnTheme.JUST_ADDED -> R.string.home_columns_theme_just_added
        HomeColumnTheme.GATHERING_DUST -> R.string.home_columns_theme_gathering_dust
        HomeColumnTheme.FAVORITES -> R.string.home_columns_theme_favorites
        HomeColumnTheme.PINNED_COLLECTION -> R.string.home_columns_theme_pinned_collection
        HomeColumnTheme.MOST_PLAYED_GENRE -> R.string.home_columns_theme_most_played_genre
    }

@get:StringRes
val HomeMediaColumnKind.labelRes: Int
    get() = when (this) {
        HomeMediaColumnKind.CONTINUE_WATCHING -> R.string.home_columns_media_continue_watching
        HomeMediaColumnKind.NEXT_UP -> R.string.home_columns_media_next_up
    }
