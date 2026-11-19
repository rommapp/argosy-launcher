package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import coil.request.ErrorResult
import coil.request.ImageResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier

/**
 * Crossfades even on a cache hit, which is what makes a poster fade in rather than pop.
 *
 * It is a data class because that fade is also what makes an identity change visible. An
 * `ImageRequest` compares its transition factory, so an ordinary class hands every recomposition
 * a request unequal to the last one, Coil restarts the load, and the fade replays from
 * transparent. Focus changes recompose, so the result reads as a flicker on the focused cover.
 */
data class AlwaysCrossfadeFactory(private val durationMillis: Int) : Transition.Factory {
    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is ErrorResult) Transition.Factory.NONE.create(target, result)
        else CrossfadeTransition(target, result, durationMillis)
}

fun alwaysCrossfadeFor(tier: MotionTier, durationMillis: Int): Transition.Factory =
    if (tier == MotionTier.Reduced || durationMillis <= 0) Transition.Factory.NONE
    else AlwaysCrossfadeFactory(durationMillis)

@Composable
@ReadOnlyComposable
fun alwaysCrossfade(durationMillis: Int): Transition.Factory =
    alwaysCrossfadeFor(LocalMotionTier.current, durationMillis)
