package com.nendo.argosy.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.nendo.argosy.data.model.HeroFocus
import com.nendo.argosy.data.model.HeroFocusSpec
import com.nendo.argosy.ui.common.LocalImageCacheManager
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * How the hero art behind one column is framed: the [spec] its focus is computed for, and how far
 * the column has grown, from 0 at side width to 1 at focused width.
 */
@Immutable
data class HomeHeroFrame(val spec: HeroFocusSpec, val widthProgress: Float)

internal fun homeHeroFocusSpec(geometry: HomeColumnsGeometry, columnHeight: Float): HeroFocusSpec {
    val height = columnHeight.coerceAtLeast(1f)
    return HeroFocusSpec(
        narrowAspect = geometry.widthAt(1f) / height,
        wideAspect = geometry.widthAt(0f) / height,
        maxZoom = ComponentDefaults.HomeColumns.heroMaxZoom,
        analysisSize = ComponentDefaults.HomeColumns.heroAnalysisSizePx
    )
}

private val HeroFocusConverter = TwoWayConverter<HeroFocus, AnimationVector4D>(
    convertToVector = { AnimationVector4D(it.narrowX, it.wideX, it.y, it.zoom) },
    convertFromVector = { HeroFocus(narrowX = it.v1, wideX = it.v2, y = it.v3, zoom = it.v4) }
)

@Composable
internal fun rememberHeroFocus(path: String?, spec: HeroFocusSpec): HeroFocus {
    val store = LocalImageCacheManager.current?.heroFocus
    val local = path?.takeIf { it.startsWith("/") }
    val reduced = reducedMotion()
    val focus = remember(store, local, spec) {
        Animatable(local?.let { store?.peek(it, spec) } ?: HeroFocus.CENTERED, HeroFocusConverter)
    }
    LaunchedEffect(focus) {
        if (store == null || local == null) return@LaunchedEffect
        val target = store.resolve(local, spec)
        if (target == focus.value) return@LaunchedEffect
        if (reduced) {
            focus.snapTo(target)
        } else {
            focus.animateTo(target, tween(Motion.durationPage, easing = Motion.argosyEase))
        }
    }
    return focus.value
}

internal fun mixHero(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction
