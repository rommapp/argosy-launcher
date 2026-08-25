package com.nendo.argosy.domain.model

/**
 * What a press on a media tile turns into once the tile has been asked what it actually stands for.
 *
 * [OpenDetail] is the answer for a series nothing playable can be found in, not a refusal: the detail
 * screen is where the seasons are, so a press that cannot resolve to an episode still lands somewhere
 * the user can pick one.
 */
sealed class MediaPlayTarget {
    data class Play(val itemId: String) : MediaPlayTarget()
    data class OpenDetail(val itemId: String) : MediaPlayTarget()
}
