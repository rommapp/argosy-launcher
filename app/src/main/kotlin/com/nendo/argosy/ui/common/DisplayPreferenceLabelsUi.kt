package com.nendo.argosy.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtShape

/**
 * The display label. `GradientPreset` lives in `data/cache` and must not import `R`, so the label
 * is attached here beside the persisted enum name.
 */
@get:StringRes
val GradientPreset.labelRes: Int
    get() = when (this) {
        GradientPreset.VIBRANT -> R.string.gradient_preset_vibrant
        GradientPreset.BALANCED -> R.string.gradient_preset_balanced
        GradientPreset.SUBTLE -> R.string.gradient_preset_subtle
        GradientPreset.CUSTOM -> R.string.gradient_preset_custom
    }

/**
 * The picker label. Every value but [BoxArtShape.NATIVE] is an aspect ratio ("2:3", "3:4", "1:1")
 * and must never be translated; only the word naming the non-ratio choice is.
 */
fun BoxArtShape.label(context: Context): String = when (this) {
    BoxArtShape.NATIVE -> context.getString(R.string.box_art_shape_native)
    else -> displayName
}
