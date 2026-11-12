package com.nendo.argosy.ui.dualscreen.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.hardware.CompanionInGameState
import com.nendo.argosy.hardware.CompanionSessionTimer
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.dualscreen.COVER_ASPECT
import com.nendo.argosy.ui.screens.gamedetail.GameDocument
import com.nendo.argosy.ui.screens.gamedetail.components.DocumentReaderController
import com.nendo.argosy.ui.screens.gamedetail.components.DocumentReaderPane
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.touchOnly
import kotlinx.coroutines.delay

enum class DashboardSection(@StringRes val labelRes: Int, val icon: ImageVector) {
    SESSION(R.string.dual_dashboard_rail_session, Icons.Filled.Timer),
    STATES(R.string.dual_dashboard_rail_states, Icons.Filled.Save),
    TROPHIES(R.string.dual_dashboard_rail_trophies, Icons.Filled.EmojiEvents),
    MANUAL(R.string.dual_dashboard_rail_manual, Icons.AutoMirrored.Filled.MenuBook),
    GUIDE(R.string.dual_dashboard_rail_guide, Icons.Filled.Map)
}

/**
 * Everything the dashboard can ask of the running game, plus opening one of its documents in
 * the dashboard's reader.
 */
class DashboardActions(
    val onQuickSave: () -> Unit,
    val onLoadState: (DashboardQuickState) -> Unit,
    val onScreenshot: () -> Unit,
    val onOpenCheats: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onQuit: () -> Unit,
    val onOpenDocument: (GameDocument) -> Unit,
    val onNextDisc: () -> Unit,
    val onToggleMute: () -> Unit
)

private val RAIL_WIDTH = DimensionTokens.Layout.companionRailWidth.dp
private val RAIL_WIDTH_WIDE = DimensionTokens.Layout.companionRailWidthWide.dp
private val RAIL_ITEM_HEIGHT = DimensionTokens.Layout.companionRailItemHeight.dp
private val RAIL_COVER_WIDTH = DimensionTokens.Layout.companionRailCoverWidth.dp
private const val WIDE_ASPECT = 1.4f
private const val BACKDROP_ALPHA = 0.7f
private const val SCRIM_ALPHA = 0.35f
private const val PANEL_ALPHA = 0.78f
private const val SELECTED_ALPHA = 0.16f
private const val CONFIRM_WINDOW_MS = 3000L

/**
 * The touch-only dashboard shown on the screen the game is not on: a rail of sections over
 * the game's blurred art, with the launcher's app bar kept along the bottom.
 */
@Composable
fun InGameDashboard(
    state: CompanionInGameState,
    controls: SessionControls,
    achievements: List<AchievementUi>,
    sessionTimer: CompanionSessionTimer?,
    reader: DocumentReaderController,
    actions: DashboardActions,
    appBar: @Composable (Modifier) -> Unit
) {
    if (!state.isLoaded) return
    val theme = LocalArgosyTheme.current
    val readerState by reader.state.collectAsState()
    val readerOpen = readerState != null
    var section by remember(state.gameId) { mutableStateOf(DashboardSection.SESSION) }
    var quitArmedUntil by remember(state.gameId) { mutableLongStateOf(0L) }
    var sessionMillis by remember { mutableLongStateOf(sessionTimer?.getActiveMillis() ?: 0L) }

    LaunchedEffect(sessionTimer) {
        if (sessionTimer == null) return@LaunchedEffect
        while (true) {
            sessionMillis = sessionTimer.getActiveMillis()
            delay(1000)
        }
    }
    LaunchedEffect(readerOpen) {
        if (!readerOpen && (section == DashboardSection.MANUAL || section == DashboardSection.GUIDE)) {
            section = DashboardSection.SESSION
        }
    }

    val statesAllowed = state.quickActionsAvailable && !state.isHardcore
    val selectSection: (DashboardSection) -> Unit = { target ->
        when (target) {
            DashboardSection.MANUAL -> state.manual?.let(actions.onOpenDocument)
            DashboardSection.GUIDE -> state.walkthrough?.let(actions.onOpenDocument)
            else -> if (readerOpen) reader.dismiss()
        }
        section = target
    }
    val density = LocalDensity.current
    var footerHeight by remember { mutableStateOf(0.dp) }
    val content = DashboardContent(
        state = state,
        controls = controls,
        achievements = achievements,
        sessionMillis = sessionMillis,
        statesAllowed = statesAllowed,
        bottomInset = footerHeight,
        actions = actions,
        onOpenSection = selectSection
    )
    val sections = buildList {
        add(DashboardSection.SESSION)
        if (statesAllowed) add(DashboardSection.STATES)
        if (content.achievementTotal > 0) add(DashboardSection.TROPHIES)
        if (state.manual != null) add(DashboardSection.MANUAL)
        if (state.walkthrough != null) add(DashboardSection.GUIDE)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.surfaceBase)) {
        val wide = maxWidth > maxHeight * WIDE_ASPECT
        AsyncImage(
            model = rememberFileImageModel(state.backgroundPath ?: state.coverPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(Motion.blurRadiusDrawer)
                .alpha(BACKDROP_ALPHA)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))

        Row(modifier = Modifier.fillMaxSize()) {
            DashboardRail(
                content = content,
                sections = sections,
                selected = section,
                wide = wide,
                controls = controls,
                quitArmed = android.os.SystemClock.elapsedRealtime() < quitArmedUntil,
                showsQuit = state.quickActionsAvailable || state.externalEmulator,
                onSelect = selectSection,
                onCheats = actions.onOpenCheats,
                onSettings = actions.onOpenSettings,
                onNextDisc = actions.onNextDisc,
                onToggleMute = actions.onToggleMute,
                onQuit = {
                    if (android.os.SystemClock.elapsedRealtime() < quitArmedUntil) {
                        quitArmedUntil = 0L
                        actions.onQuit()
                    } else {
                        quitArmedUntil = android.os.SystemClock.elapsedRealtime() + CONFIRM_WINDOW_MS
                    }
                }
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    DashboardSection.SESSION -> SessionSection(content, wide)
                    DashboardSection.STATES -> StatesSection(content, wide)
                    DashboardSection.TROPHIES -> TrophiesSection(content)
                    DashboardSection.MANUAL, DashboardSection.GUIDE -> Unit
                }
                if (wide && readerOpen) {
                    DashboardReader(reader, Modifier.fillMaxSize().padding(bottom = footerHeight))
                }
            }
        }
        appBar(
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
        )
        if (!wide && readerOpen) DashboardReader(reader, Modifier.fillMaxSize())
    }
}

/**
 * The data and callbacks every section reads, gathered once per composition.
 */
class DashboardContent(
    val state: CompanionInGameState,
    val controls: SessionControls,
    val achievements: List<AchievementUi>,
    val sessionMillis: Long,
    val statesAllowed: Boolean,
    val bottomInset: Dp,
    val actions: DashboardActions,
    val onOpenSection: (DashboardSection) -> Unit
) {
    val achievementTotal: Int =
        if (achievements.isNotEmpty()) achievements.size else state.achievementCount
    val achievementEarned: Int =
        if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else state.earnedAchievementCount
}

@Composable
private fun DashboardReader(reader: DocumentReaderController, modifier: Modifier) {
    DocumentReaderPane(
        reader = reader,
        showsControllerHints = false,
        onDismiss = reader::dismiss,
        modifier = modifier
    )
}

@Composable
private fun DashboardRail(
    content: DashboardContent,
    sections: List<DashboardSection>,
    selected: DashboardSection,
    wide: Boolean,
    controls: SessionControls,
    quitArmed: Boolean,
    showsQuit: Boolean,
    onSelect: (DashboardSection) -> Unit,
    onCheats: () -> Unit,
    onSettings: () -> Unit,
    onNextDisc: () -> Unit,
    onToggleMute: () -> Unit,
    onQuit: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .width(if (wide) RAIL_WIDTH_WIDE else RAIL_WIDTH)
            .fillMaxHeight()
            .background(theme.surfaceBase.copy(alpha = PANEL_ALPHA))
            .padding(bottom = content.bottomInset)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (wide) RailHeader(content.state)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            items(sections, key = { it.name }) { section ->
                RailItem(
                    icon = section.icon,
                    label = stringResource(section.labelRes),
                    trailing = railTrailing(section, content),
                    trailingColor = if (section == DashboardSection.TROPHIES) ALauncherColors.TrophyAmber else null,
                    selected = section == selected,
                    wide = wide,
                    onTap = { onSelect(section) }
                )
            }
            if (controls.cheatsAvailable) {
                item(key = "cheats") {
                    RailItem(
                        icon = Icons.Filled.AutoFixHigh,
                        label = stringResource(R.string.dual_dashboard_rail_cheats),
                        wide = wide,
                        onTap = onCheats
                    )
                }
            }
            if (controls.discSwapAvailable) {
                item(key = "next_disc") {
                    RailItem(
                        icon = Icons.Filled.Album,
                        label = stringResource(R.string.stream_dashboard_rail_next_disc),
                        wide = wide,
                        onTap = onNextDisc
                    )
                }
            }
            if (controls.muteAvailable) {
                item(key = "mute") {
                    RailItem(
                        icon = if (controls.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        label = if (controls.isMuted) {
                            stringResource(R.string.stream_dashboard_rail_unmute)
                        } else {
                            stringResource(R.string.stream_dashboard_rail_mute)
                        },
                        wide = wide,
                        onTap = onToggleMute
                    )
                }
            }
            if (controls.settingsAvailable) {
                item(key = "settings") {
                    RailItem(
                        icon = Icons.Filled.Tune,
                        label = stringResource(R.string.dual_dashboard_rail_settings),
                        wide = wide,
                        onTap = onSettings
                    )
                }
            }
        }
        if (showsQuit) {
            RailItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = if (quitArmed) {
                    stringResource(R.string.dual_dashboard_rail_quit_confirm)
                } else {
                    stringResource(R.string.dual_dashboard_rail_quit)
                },
                tint = theme.destructive,
                selected = quitArmed,
                wide = wide,
                onTap = onQuit
            )
        }
    }
}

@Composable
private fun railTrailing(section: DashboardSection, content: DashboardContent): String? = when (section) {
    DashboardSection.TROPHIES -> if (content.achievementTotal > 0) {
        stringResource(
            R.string.dual_dashboard_achievements_progress,
            content.achievementEarned,
            content.achievementTotal
        )
    } else {
        null
    }
    DashboardSection.MANUAL -> content.state.manualLastPage?.let {
        stringResource(R.string.dual_dashboard_rail_page, it + 1)
    }
    DashboardSection.GUIDE -> content.state.walkthroughProgress?.takeIf { it > 0f }?.let {
        stringResource(R.string.dual_dashboard_rail_percent, readPercent(it))
    }
    else -> null
}

@Composable
private fun RailHeader(state: CompanionInGameState) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.padding(start = Dimens.spacingXs, end = Dimens.spacingXs, bottom = Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = rememberFileImageModel(state.coverPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(RAIL_COVER_WIDTH)
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )
        Column {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.platformName,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    wide: Boolean,
    onTap: () -> Unit,
    trailing: String? = null,
    trailingColor: Color? = null,
    selected: Boolean = false,
    tint: Color? = null
) {
    val theme = LocalArgosyTheme.current
    val color = tint ?: if (selected) theme.textPrimary else theme.textDim
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val base = Modifier
        .fillMaxWidth()
        .height(RAIL_ITEM_HEIGHT)
        .clip(shape)
        .background(if (selected) (tint ?: theme.focusAccent).copy(alpha = SELECTED_ALPHA) else Color.Transparent)
        .touchOnly(onTap)
    if (wide) {
        Row(
            modifier = base.padding(horizontal = Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimens.iconMd))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = trailingColor ?: theme.textDim
                )
            }
        }
    } else {
        Column(
            modifier = base,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(Dimens.iconMd))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
