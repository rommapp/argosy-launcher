package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.SortOption

/**
 * The display label for a sort order.
 *
 * `SortOption` lives in `data/model` and must not import `R`, so the label is attached here
 * instead, following the shape of [CompletionStatusUi]. The enum keeps its name as the stored
 * and compared value.
 */
@get:StringRes
val SortOption.labelRes: Int
    get() = when (this) {
        SortOption.TITLE -> R.string.sort_title
        SortOption.RATING -> R.string.sort_rating
        SortOption.USER_RATING -> R.string.sort_user_rating
        SortOption.DIFFICULTY -> R.string.sort_difficulty
        SortOption.RELEASE_YEAR -> R.string.sort_release_year
        SortOption.PLAY_COUNT -> R.string.sort_play_count
        SortOption.PLAY_TIME -> R.string.sort_play_time
        SortOption.LAST_PLAYED -> R.string.sort_last_played
        SortOption.RECENTLY_ADDED -> R.string.sort_recently_added
    }
