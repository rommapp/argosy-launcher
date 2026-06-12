// AUTO-GENERATED. DO NOT EDIT.
// Source: design-system-docs/tokens.json
// Run: node scripts/gen-tokens.mjs

@file:Suppress("unused")

package com.nendo.argosy.ui.theme.generated

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

object MotionTokens {
    object Spring {
        const val focusDampingRatio = 0.6f
        const val focusStiffness = 400f
        val focus: AnimationSpec<Float> = spring(dampingRatio = focusDampingRatio, stiffness = focusStiffness)
        val focusDp: AnimationSpec<Dp> = spring(dampingRatio = focusDampingRatio, stiffness = focusStiffness)
        val focusColor: AnimationSpec<Color> = spring(dampingRatio = focusDampingRatio, stiffness = focusStiffness)
    }

    object Tween {
        const val fastMs = 100
        val fast: AnimationSpec<Float> = tween(durationMillis = fastMs)
        const val mediumMs = 500
        val medium: AnimationSpec<Float> = tween(durationMillis = mediumMs)
        const val longMs = 1200
        val long: AnimationSpec<Float> = tween(durationMillis = longMs)
        const val shimmerMs = 2000
        val shimmer: AnimationSpec<Float> = tween(durationMillis = shimmerMs)
    }

}
