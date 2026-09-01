package com.nendo.argosy.ui.common

import coil.request.ErrorResult
import coil.request.ImageResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget

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
