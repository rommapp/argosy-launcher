package com.nendo.argosy.ui.common

import coil.request.ErrorResult
import coil.request.ImageResult
import coil.transition.CrossfadeTransition
import coil.transition.Transition
import coil.transition.TransitionTarget

class AlwaysCrossfadeFactory(private val durationMillis: Int) : Transition.Factory {
    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is ErrorResult) Transition.Factory.NONE.create(target, result)
        else CrossfadeTransition(target, result, durationMillis)
}
