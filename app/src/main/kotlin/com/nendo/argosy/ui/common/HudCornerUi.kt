package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.HudCorner

/**
 * The token and the label for the in-game HUD corner, kept apart.
 *
 * The stored value used to be the English label itself, which made the preference unreadable
 * the moment that text changed for any reason. It is now the enum name, and [hudCornerFromStored]
 * still accepts the old labels so a device upgrading from an earlier build, or a settings backup
 * exported by one, keeps the corner the user picked. [legacyLabel] exists ONLY for that
 * read-compat match; it is not localized and must never be rendered. Use [labelRes] to display
 * the corner name.
 */
val HudCorner.legacyLabel: String
    get() = when (this) {
        HudCorner.TOP_LEFT -> "Top Left"
        HudCorner.TOP_RIGHT -> "Top Right"
        HudCorner.BOTTOM_LEFT -> "Bottom Left"
        HudCorner.BOTTOM_RIGHT -> "Bottom Right"
    }

@get:StringRes
val HudCorner.labelRes: Int
    get() = when (this) {
        HudCorner.TOP_LEFT -> R.string.settings_hudcorner_top_left
        HudCorner.TOP_RIGHT -> R.string.settings_hudcorner_top_right
        HudCorner.BOTTOM_LEFT -> R.string.settings_hudcorner_bottom_left
        HudCorner.BOTTOM_RIGHT -> R.string.settings_hudcorner_bottom_right
    }

fun hudCornerFromStored(value: String?): HudCorner {
    if (value == null) return HudCorner.TOP_LEFT
    HudCorner.entries.firstOrNull { it.name == value }?.let { return it }
    return HudCorner.entries.firstOrNull { it.legacyLabel == value } ?: HudCorner.TOP_LEFT
}
