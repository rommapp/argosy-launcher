package com.nendo.argosy.ui.components.launch

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The art a surface shows for the game it would launch, and where: [bounds] in root coordinates of
 * the window on [displayId]. The launch overlay grows this art to full screen while that game's
 * launch runs.
 */
data class LaunchHeroOrigin(
    val gameId: Long,
    val displayId: Int,
    val bounds: Rect,
    val artPath: String,
    val artIsScreenshot: Boolean
)

/**
 * The [LaunchHeroOrigin] the visible surface currently offers, or null when none does.
 */
@Singleton
class LaunchHeroOrigins @Inject constructor() {
    private val _origin = MutableStateFlow<LaunchHeroOrigin?>(null)
    val origin: StateFlow<LaunchHeroOrigin?> = _origin.asStateFlow()

    fun offer(origin: LaunchHeroOrigin?) {
        _origin.value = origin
    }

    fun withdraw(displayId: Int) {
        _origin.update { current -> current?.takeUnless { it.displayId == displayId } }
    }
}

/**
 * The origin to grow for a launch of [gameId] drawn on [displayId], when the game opens there.
 */
fun heroOriginFor(origin: LaunchHeroOrigin?, gameId: Long?, displayId: Int, gameOpensHere: Boolean): LaunchHeroOrigin? =
    origin?.takeIf { gameOpensHere && gameId != null && it.gameId == gameId && it.displayId == displayId }
