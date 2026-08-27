package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.GlowColorMode

/**
 * Display label for the glow-colour row. How glow actually reads these modes belongs to
 * `Modifier.boxArtFrame` in `ui/components/BoxArtFrame.kt`; this file only attaches the
 * translatable label beside it, the same split `CompletionStatusUi.kt` uses for domain enums.
 */
@get:StringRes
val GlowColorMode.labelRes: Int
    get() = when (this) {
        GlowColorMode.AUTO -> R.string.settings_box_art_glow_color_auto
        GlowColorMode.ACCENT -> R.string.settings_box_art_glow_color_accent
        GlowColorMode.ACCENT_GRADIENT -> R.string.settings_box_art_glow_color_theme_gradient
        GlowColorMode.COVER -> R.string.settings_box_art_glow_color_cover
    }
