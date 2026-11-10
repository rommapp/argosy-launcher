package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

enum class FooterVariant { STANDARD, SUBTLE }

data class FooterEntry(
    val hints: List<FooterHintItem>,
    val style: FooterStyleConfig,
    val variant: FooterVariant,
    val onHintClick: ((InputButton) -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val forced: Boolean = false,
    val reservesSpace: Boolean = true,
    val leadingContent: (@Composable () -> Unit)? = null
)

/** Singleton hint stack backing the app-root guide bar; the most recently registered active surface wins. */
class FooterHostController {
    private var nextId = 0L
    private val stack = mutableStateListOf<Pair<Long, FooterEntry>>()

    internal fun allocateId(): Long = nextId++

    internal fun set(id: Long, entry: FooterEntry) {
        val index = stack.indexOfFirst { it.first == id }
        if (index >= 0) stack[index] = id to entry else stack.add(id to entry)
    }

    internal fun remove(id: Long) {
        stack.removeAll { it.first == id }
    }

    val top: FooterEntry? get() = stack.lastOrNull()?.second

    internal val spaceEntry: FooterEntry?
        get() = stack.lastOrNull { it.second.reservesSpace }?.second

    private val tappableHints = mutableMapOf<Any, LayoutCoordinates>()

    internal fun trackTappableHint(key: Any, coordinates: LayoutCoordinates) {
        tappableHints[key] = coordinates
    }

    internal fun untrackTappableHint(key: Any) {
        tappableHints.remove(key)
    }

    fun hasTappableHintAt(windowPosition: Offset): Boolean =
        isBarVisible && tappableHints.values.any { coordinates ->
            coordinates.isAttached && coordinates.boundsInWindow().contains(windowPosition)
        }
}

val LocalFooterHost = staticCompositionLocalOf { FooterHostController() }
val LocalFooterHostVisible = staticCompositionLocalOf { true }
internal val LocalFooterHintTracker = staticCompositionLocalOf<FooterHostController?> { null }

/**
 * Bottom space held by an always-visible floating nav bar, on top of whatever the guide bar holds.
 */
val LocalNavBarClearance = compositionLocalOf { 0.dp }

/** Registers gamepad hints on the app-root guide bar for the lifetime of the calling composable. */
@Composable
fun FooterHints(
    hints: List<Pair<InputButton, String>>,
    variant: FooterVariant = FooterVariant.STANDARD,
    onHintClick: ((InputButton) -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    forced: Boolean = false,
    reservesSpace: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    FooterHintsWithState(
        hints = remember(hints) { hints.map { FooterHintItem(it.first, it.second) } },
        variant = variant,
        onHintClick = onHintClick,
        trailingContent = trailingContent,
        forced = forced,
        reservesSpace = reservesSpace,
        leadingContent = leadingContent
    )
}

/** FooterHints variant carrying per-hint enabled state. */
@Composable
fun FooterHintsWithState(
    hints: List<FooterHintItem>,
    variant: FooterVariant = FooterVariant.STANDARD,
    onHintClick: ((InputButton) -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    forced: Boolean = false,
    reservesSpace: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val controller = LocalFooterHost.current
    val style = LocalFooterStyle.current
    val id = remember(controller) { controller.allocateId() }
    val entry = FooterEntry(hints, style, variant, onHintClick, trailingContent, forced, reservesSpace, leadingContent)
    SideEffect { controller.set(id, entry) }
    DisposableEffect(controller, id) {
        onDispose { controller.remove(id) }
    }
}

/** Renders the top of the hint stack; composed exactly once at the app root. */
@Composable
fun FooterHost(
    controller: FooterHostController,
    modifier: Modifier = Modifier
) {
    val entry = controller.top
    CompositionLocalProvider(
        LocalFooterStyle provides (entry?.style ?: FooterStyleConfig()),
        LocalFooterHintTracker provides controller
    ) {
        when (entry?.variant) {
            FooterVariant.SUBTLE -> SubtleFooterBar(
                hints = entry.hints.map { it.button to it.action },
                modifier = modifier,
                onHintClick = entry.onHintClick
            )
            else -> FooterBarWithState(
                hints = entry?.hints ?: emptyList(),
                modifier = modifier,
                onHintClick = entry?.onHintClick,
                trailingContent = entry?.trailingContent,
                leadingContent = entry?.leadingContent,
                forceVisible = entry?.forced == true
            )
        }
    }
}

val FooterHostController.isBarVisible: Boolean
    get() = spaceEntry?.let { entry ->
        entry.forced || entry.leadingContent != null || entry.hints.any { !isObviousHint(it.button) }
    } == true

@Composable
fun FooterSpacer() {
    val controller = LocalFooterHost.current
    val isBarVisible = LocalFooterHostVisible.current && controller.isBarVisible
    val height by animateDpAsState(
        targetValue = if (isBarVisible) Dimens.footerHeight else 0.dp,
        animationSpec = tween(Motion.durationContent, easing = Motion.argosyEase),
        label = "footer-spacer",
    )
    Spacer(Modifier.height(height + LocalNavBarClearance.current))
}

@Composable
fun withNavBarClearance(bottom: Dp): Dp = bottom + LocalNavBarClearance.current

@Composable
fun NavBarClearanceSpacer() {
    Spacer(Modifier.height(LocalNavBarClearance.current))
}
