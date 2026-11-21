package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.DrawerItem
import com.nendo.argosy.ui.DrawerState
import com.nendo.argosy.ui.drawerFocusIndexOf
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private const val MAIN_DRAWER_SLOT_OWNER = "navigationMenu"
private const val MAIN_DRAWER_DISMISS_TAP = -1

@Composable
internal fun mainDrawerScrimColor(): Color =
    if (LocalLauncherTheme.current.isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)

/**
 * Publishes the open navigation menu to the presentation screen and runs the taps made there:
 * a tap on a row arrives as its drawer focus index and goes to [onNavigate], a tap beside the
 * menu goes to [onDismiss]. The pad stays on the drawer handler of the hosting launcher.
 */
@Composable
fun MainDrawerOnPresentation(
    holder: Any,
    items: List<DrawerItem>,
    currentRoute: String?,
    drawerState: DrawerState,
    routeAtFocus: (Int) -> String?,
    onNavigate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val manager = DualScreenManagerHolder.instance ?: return
    val owner = remember(holder) { SlotOwner.of(MAIN_DRAWER_SLOT_OWNER, holder) }
    PresentOnCompanion(
        owner = owner,
        slot = PresentationSlot.NavigationMenu(owner, items, currentRoute, drawerState)
    )

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRouteAtFocus by rememberUpdatedState(routeAtFocus)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(manager, owner, lifecycle) {
        manager.presentationTap
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .filter { it.owner == owner }
            .collect { tap ->
                if (!manager.consumePresentationTap(tap)) return@collect
                if (tap.index == MAIN_DRAWER_DISMISS_TAP) {
                    currentOnDismiss()
                } else {
                    currentRouteAtFocus(tap.index)?.let(currentOnNavigate)
                }
            }
    }
}

/**
 * The navigation menu as the presentation screen draws it: the shared [MainDrawer] over a scrim.
 * Every tap is reported through [onTap] as a drawer focus index, or as a dismissal for a tap on
 * the scrim, for the hosting launcher to run.
 */
@Composable
fun MainDrawerPresentation(
    slot: PresentationSlot.NavigationMenu,
    onTap: (Int) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(mainDrawerScrimColor())
                .clickableNoFocus { onTap(MAIN_DRAWER_DISMISS_TAP) }
        )
        MainDrawer(
            items = slot.items,
            currentRoute = slot.currentRoute,
            drawerState = slot.drawerState,
            isOpen = true,
            onNavigate = { route -> drawerFocusIndexOf(slot.items, route)?.let(onTap) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
        )
    }
}
