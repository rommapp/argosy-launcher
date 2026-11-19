package com.nendo.argosy.domain.model

/**
 * The one rule for reduced motion: the user's toggle, or Android's animator scale at zero.
 */
fun isReducedMotion(reduceAnimations: Boolean, animatorsEnabled: Boolean): Boolean =
    reduceAnimations || !animatorsEnabled
