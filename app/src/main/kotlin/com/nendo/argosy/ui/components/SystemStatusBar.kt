package com.nendo.argosy.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.LocalOpenRunningGames
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.common.RunningGamesUi
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.domain.model.BatteryThresholds
import com.nendo.argosy.hardware.toBatterySample
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.TypographyTokens
import com.nendo.argosy.util.formatClockTime
import kotlinx.coroutines.delay

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false
)

@Composable
fun rememberBatteryState(): State<BatteryState> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryState()) }

    DisposableEffect(context) {
        fun publish(intent: Intent) {
            val sample = intent.toBatterySample() ?: return
            batteryState.value = BatteryState(level = sample.level, isCharging = sample.charging)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                publish(intent)
            }
        }

        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { publish(it) }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryState
}

/**
 * Whether artwork is still being fetched, for the status bar to show without every surface that
 * draws one having to be told. The bar appears on both displays and in the drawer; a parameter
 * would mean each caller remembering to pass it, which is how the companion screen ends up missing
 * what the main one shows.
 */
val LocalArtworkScraping = androidx.compose.runtime.compositionLocalOf { false }

data class StatusBarItems(
    val clock: Boolean = true,
    val battery: Boolean = true,
    val network: Boolean = false
)

enum class NetworkLink { OFFLINE, WIFI, ETHERNET, CELLULAR }

@Composable
fun rememberNetworkLink(): State<NetworkLink> {
    val context = LocalContext.current
    val link = remember { mutableStateOf(NetworkLink.OFFLINE) }

    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                link.value = caps.toNetworkLink()
            }

            override fun onLost(network: Network) {
                link.value = NetworkLink.OFFLINE
            }
        }
        link.value = manager
            ?.let { it.getNetworkCapabilities(it.activeNetwork) }
            .toNetworkLink()
        manager?.registerDefaultNetworkCallback(callback)

        onDispose { manager?.unregisterNetworkCallback(callback) }
    }

    return link
}

private fun NetworkCapabilities?.toNetworkLink(): NetworkLink {
    if (this == null || !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        return NetworkLink.OFFLINE
    }
    return when {
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkLink.ETHERNET
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkLink.WIFI
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkLink.CELLULAR
        else -> NetworkLink.OFFLINE
    }
}

val LocalStatusBarItems = androidx.compose.runtime.compositionLocalOf { StatusBarItems() }

val LocalTransientHomeStatusVisible = androidx.compose.runtime.compositionLocalOf { false }

fun statusBarItemsOf(prefs: com.nendo.argosy.data.preferences.UserPreferences) = StatusBarItems(
    clock = prefs.showStatusClock,
    battery = prefs.showStatusBattery,
    network = prefs.showStatusNetwork
)

/**
 * Provides [LocalStatusBarItems] from [preferences] for a surface that renders outside ArgosyApp,
 * so its status bar follows the Interface toggles as they change.
 */
@Composable
fun ProvideStatusBarItems(
    preferences: kotlinx.coroutines.flow.Flow<com.nendo.argosy.data.preferences.UserPreferences>,
    content: @Composable () -> Unit
) {
    val prefs by preferences.collectAsState(initial = null)
    val items = prefs?.let(::statusBarItemsOf) ?: StatusBarItems()
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusBarItems provides items, content = content)
}

/**
 * [scrim] backs the bar with a plate so it reads over artwork. Turn it off on a flat surface,
 * where the plate has nothing to separate the bar from and only shows as a panel of its own.
 * [allowWrap] places network and battery below the clock when network status is shown.
 */
@Composable
fun SystemStatusBar(
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified,
    scrim: Boolean = true,
    allowWrap: Boolean = false,
    showsRunningSlots: Boolean = true
) {
    val isScrapingArtwork = LocalArtworkScraping.current
    val shown = LocalStatusBarItems.current
    if (!hasStatusBarContent(shown, isScrapingArtwork)) return
    val effectiveColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        contentColor
    }
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val batteryState by rememberBatteryState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    val statusModifier = modifier
        .then(if (scrim) Modifier.heightIn(min = frostedVisualChromeHeight).frostedSurface() else Modifier)
        .padding(
            horizontal = if (scrim) {
                ComponentDefaults.FrostedSurface.statusPaddingHorizontalDp.dp * LocalUiScale.current.scale
            } else ComponentDefaults.OverlayLegibility.scrimPaddingHorizDp.dp,
            vertical = if (scrim) Dimens.spacingSm else ComponentDefaults.OverlayLegibility.scrimPaddingVertDp.dp
        )
    val content: @Composable (Modifier, Modifier) -> Unit = { clockModifier, itemModifier ->
        if (isScrapingArtwork) {
            ArtworkScrapeIndicator(color = effectiveColor, modifier = itemModifier)
        }

        if (shown.clock) {
            Text(
                text = formatClockTime(LocalContext.current, currentTime.longValue),
                modifier = clockModifier,
                style = chromeTextStyle(
                    MaterialTheme.typography.titleMedium,
                    TypographyTokens.titleMedium,
                    ComponentDefaults.FrostedSurface.chromeFontSizeSp.sp
                ),
                color = effectiveColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

    }
    val running = LocalRunningGames.current.takeIf { showsRunningSlots } ?: RunningGamesUi()
    val indicators: @Composable (Modifier) -> Unit = { itemModifier ->
        if (shown.network || shown.battery || running.isAnyRunning) {
            Row(
                modifier = itemModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (running.isAnyRunning) {
                    RunningSlotStrip(running = running, onOpen = LocalOpenRunningGames.current)
                }
                if (shown.network) {
                    NetworkIndicator(color = effectiveColor)
                }
                if (shown.battery) {
                    BatteryIndicator(
                        level = batteryState.level,
                        isCharging = batteryState.isCharging,
                        color = effectiveColor
                    )
                }
            }
        }
    }
    if (allowWrap && shown.network) {
        Column(
            modifier = statusModifier,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            if (shown.clock || isScrapingArtwork) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    content(Modifier.weight(1f, fill = false), Modifier)
                }
            }
            indicators(Modifier)
        }
    } else {
        Row(
            modifier = statusModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            content(Modifier.weight(1f, fill = false), Modifier)
            indicators(Modifier)
        }
    }
}

internal fun hasStatusBarContent(items: StatusBarItems, scrapingArtwork: Boolean): Boolean =
    items.clock || items.battery || items.network || scrapingArtwork

@Composable
private fun RunningSlotStrip(running: RunningGamesUi, onOpen: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val strip = ComponentDefaults.RunningStrip
    val unitShape = RoundedCornerShape(strip.unitRadiusDp.dp)
    val unitGap = strip.unitGapDp.dp
    val loaded = theme.focusAccent.copy(alpha = strip.loadedAlpha)
    val external = theme.textPrimary.copy(alpha = strip.externalAlpha)
    val description = stringResource(R.string.ui_status_bar_running_desc)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickableNoFocus(onClick = onOpen)
            .semantics { contentDescription = description }
            .padding(horizontal = Dimens.spacingXs, vertical = Dimens.borderMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(unitGap)
    ) {
        running.slotUnits.forEach { unit ->
            StripUnit(
                width = strip.unitWidthDp.dp * unit.weight + unitGap * (unit.weight - 1),
                color = loaded,
                shape = unitShape,
                closingBase = theme.surfaceElevated.takeIf { unit.isClosing }
            )
        }
        repeat(running.freeUnits) {
            StripUnit(width = strip.unitWidthDp.dp, color = theme.surfaceElevated, shape = unitShape)
        }
        running.outsidePoolUnits.forEachIndexed { index, unit ->
            if (index == 0 && (running.slotUnits.isNotEmpty() || running.freeUnits > 0)) {
                Spacer(modifier = Modifier.width(strip.externalGapDp.dp - unitGap))
            }
            StripUnit(
                width = strip.unitWidthDp.dp,
                color = external,
                shape = unitShape,
                closingBase = theme.surfaceElevated.takeIf { unit.isClosing }
            )
        }
    }
}

@Composable
private fun StripUnit(width: Dp, color: Color, shape: RoundedCornerShape, closingBase: Color? = null) {
    Box(
        modifier = Modifier
            .width(width)
            .height(ComponentDefaults.RunningStrip.unitHeightDp.dp)
            .clip(shape)
            .then(
                if (closingBase == null) {
                    Modifier.background(color)
                } else {
                    Modifier.background(closingBase).drawBehind { drawClosingStripes(color) }
                }
            )
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClosingStripes(color: Color) {
    val stripe = ComponentDefaults.RunningStrip.closingStripeDp.dp.toPx()
    val period = stripe * 2
    var start = -size.height
    while (start < size.width) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(start, size.height)
            lineTo(start + stripe, size.height)
            lineTo(start + stripe + size.height, 0f)
            lineTo(start + size.height, 0f)
            close()
        }
        drawPath(path, color)
        start += period
    }
}

/**
 * Says that artwork is still being fetched, without saying what. It pulses so it reads as work in
 * progress rather than a warning; the detail lives in Settings > Sync for anyone who wants it.
 */
@Composable
private fun ArtworkScrapeIndicator(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "artworkScrape")
    val alpha = transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "artworkScrapeAlpha"
    )
    Icon(
        imageVector = Icons.Outlined.Image,
        contentDescription = null,
        tint = color,
        modifier = modifier
            .size(Dimens.iconSm)
            .graphicsLayer { this.alpha = alpha.value }
    )
}

@Composable
private fun NetworkIndicator(color: Color, modifier: Modifier = Modifier) {
    val link by rememberNetworkLink()
    val icon = when (link) {
        NetworkLink.OFFLINE -> Icons.Outlined.WifiOff
        NetworkLink.WIFI -> Icons.Outlined.Wifi
        NetworkLink.ETHERNET -> Icons.Outlined.SettingsEthernet
        NetworkLink.CELLULAR -> Icons.Outlined.SignalCellularAlt
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = modifier.size(Dimens.iconSm)
    )
}

@Composable
private fun BatteryIndicator(
    level: Int,
    isCharging: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        BatteryIcon(
            level = level,
            isCharging = isCharging,
            color = color,
            modifier = Modifier.size(width = Dimens.iconMd, height = Dimens.radiusLg)
        )
        Text(
            text = "$level%",
            style = chromeTextStyle(
                MaterialTheme.typography.titleMedium,
                TypographyTokens.titleMedium,
                ComponentDefaults.FrostedSurface.chromeFontSizeSp.sp *
                    ComponentDefaults.FrostedSurface.statusBatteryFontSizeRatio
            ),
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun BatteryIcon(
    level: Int,
    isCharging: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fillColor = when {
        isCharging -> ColorTokens.Domain.Battery.charging
        level <= BatteryThresholds.LOW_PERCENT -> ColorTokens.Domain.Battery.low
        else -> color
    }

    Canvas(modifier = modifier) {
        val bodyWidth = size.width - 4.dp.toPx()
        val bodyHeight = size.height
        val cornerRadius = 2.dp.toPx()
        val strokeWidth = 1.5f.dp.toPx()
        val padding = strokeWidth
        val terminalWidth = ComponentDefaults.BatteryIndicator.terminalWidthDp.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth)
        )

        drawRect(
            color = color,
            topLeft = Offset(bodyWidth, bodyHeight * 0.3f),
            size = Size(terminalWidth, bodyHeight * 0.4f)
        )

        val fillWidth = ((bodyWidth - padding * 2) * (level / 100f)).coerceAtLeast(0f)
        if (fillWidth > 0) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(padding, padding),
                size = Size(fillWidth, bodyHeight - padding * 2),
                cornerRadius = CornerRadius(cornerRadius / 2, cornerRadius / 2)
            )
        }

        if (isCharging) {
            val centerX = bodyWidth / 2
            val centerY = bodyHeight / 2
            val boltSize = bodyHeight * 0.6f

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX + boltSize * 0.1f, centerY - boltSize * 0.4f)
                lineTo(centerX - boltSize * 0.2f, centerY)
                lineTo(centerX + boltSize * 0.05f, centerY)
                lineTo(centerX - boltSize * 0.1f, centerY + boltSize * 0.4f)
                lineTo(centerX + boltSize * 0.2f, centerY)
                lineTo(centerX - boltSize * 0.05f, centerY)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
