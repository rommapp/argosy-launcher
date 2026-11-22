package com.nendo.argosy.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nendo.argosy.ui.components.LaunchProgressEntryPoint
import com.nendo.argosy.ui.components.launch.LaunchHeroOrigin
import dagger.hilt.android.EntryPointAccessors

@Composable
internal fun OfferColumnsLaunchHero(enabled: Boolean, item: HomeRowItem?, columnBounds: Rect?) {
    val context = LocalContext.current
    val origins = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LaunchProgressEntryPoint::class.java)
            .launchHeroOrigins()
    }
    val displayId = ContextCompat.getDisplayOrDefault(context).displayId
    val game = (item as? HomeRowItem.Game)?.game?.takeIf { enabled }
    val art = game?.let { rememberHomeBackgroundArt(it) }
    val offer = if (game != null && art != null && columnBounds != null) {
        LaunchHeroOrigin(game.id, displayId, columnBounds, art.path, art.isScreenshot)
    } else {
        null
    }
    LaunchedEffect(offer, displayId) {
        if (offer != null) origins.offer(offer) else origins.withdraw(displayId)
    }
    DisposableEffect(origins, displayId) {
        onDispose { origins.withdraw(displayId) }
    }
}
