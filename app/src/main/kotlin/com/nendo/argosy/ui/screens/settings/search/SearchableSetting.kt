package com.nendo.argosy.ui.screens.settings.search

import androidx.annotation.StringRes

/**
 * A settings row the settings search can find. A section registered as searchable derives row
 * focusability from this type, so a focusable row cannot exist without the label its screen
 * renders and the search index matches.
 */
interface SearchableSetting {
    @get:StringRes
    val labelRes: Int

    @get:StringRes
    val keywordsRes: Int?
        get() = null
}
