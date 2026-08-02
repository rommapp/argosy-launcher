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
        const val focusSnappyDampingRatio = 0.55f
        const val focusSnappyStiffness = 700f
        val focusSnappy: AnimationSpec<Float> = spring(dampingRatio = focusSnappyDampingRatio, stiffness = focusSnappyStiffness)
        val focusSnappyDp: AnimationSpec<Dp> = spring(dampingRatio = focusSnappyDampingRatio, stiffness = focusSnappyStiffness)
        val focusSnappyColor: AnimationSpec<Color> = spring(dampingRatio = focusSnappyDampingRatio, stiffness = focusSnappyStiffness)
        const val focusReducedDampingRatio = 1f
        const val focusReducedStiffness = 1200f
        val focusReduced: AnimationSpec<Float> = spring(dampingRatio = focusReducedDampingRatio, stiffness = focusReducedStiffness)
        val focusReducedDp: AnimationSpec<Dp> = spring(dampingRatio = focusReducedDampingRatio, stiffness = focusReducedStiffness)
        val focusReducedColor: AnimationSpec<Color> = spring(dampingRatio = focusReducedDampingRatio, stiffness = focusReducedStiffness)
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
        const val microMs = 120
        val micro: AnimationSpec<Float> = tween(durationMillis = microMs)
        const val contentMs = 180
        val content: AnimationSpec<Float> = tween(durationMillis = contentMs)
        const val slideMs = 200
        val slide: AnimationSpec<Float> = tween(durationMillis = slideMs)
        const val pageMs = 250
        val page: AnimationSpec<Float> = tween(durationMillis = pageMs)
        const val drawerMs = 450
        val drawer: AnimationSpec<Float> = tween(durationMillis = drawerMs)
    }

}
