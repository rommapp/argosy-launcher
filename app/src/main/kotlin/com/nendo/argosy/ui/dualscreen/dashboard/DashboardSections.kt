package com.nendo.argosy.ui.dualscreen.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.ScreenshotImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.dualscreen.COVER_ASPECT
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementList
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.touchOnly
import com.nendo.argosy.ui.util.verticalEdgeFade
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import java.io.File
import kotlinx.coroutines.delay

private val SESSION_COVER_WIDTH = DimensionTokens.Layout.companionSessionCoverWidth.dp
private const val STATE_THUMB_ASPECT = 4f / 3f
private val PROGRESS_HEIGHT = DimensionTokens.Layout.companionProgressHeight.dp
private const val RECENT_STATE_COUNT = 4
private const val HEADER_ACTIONS_WEIGHT = 0.6f
private const val STATE_GRID_COLUMNS = 3
private const val STATE_GRID_COLUMNS_WIDE = 4
private const val CARD_ALPHA = 0.72f
private const val CONFIRM_WINDOW_MS = 3000L

@Composable
internal fun SessionSection(content: DashboardContent, wide: Boolean) {
    StackedSession(content, showsCover = !wide)
}

@Composable
private fun StackedSession(content: DashboardContent, showsCover: Boolean) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = content.bottomInset)
            .verticalEdgeFade(listState, fadeHeight = Dimens.spacingSm + Dimens.spacingXs, top = false),
        contentPadding = PaddingValues(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        item { SessionHeader(content, showsCover = showsCover) }
        item { RecentStates(content) }
        documentCards(content)
        item { AchievementCard(content) }
    }
}

@Composable
internal fun StatesSection(content: DashboardContent, wide: Boolean) {
    val theme = LocalArgosyTheme.current
    val edge = if (wide) Dimens.spacingXl else Dimens.spacingLg
    Column(
        modifier = Modifier.fillMaxSize().padding(start = edge, end = edge, top = edge),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = stringResource(R.string.dual_dashboard_states_title),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary
        )
        ActionRow(content)
        val states = content.controls.quickStates
        if (states.isEmpty()) {
            Text(
                text = stringResource(R.string.dual_dashboard_states_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )
            return@Column
        }
        var armed by remember { mutableStateOf(ArmedState()) }
        ClearAt(armed.until) { armed = ArmedState() }
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(if (wide) STATE_GRID_COLUMNS_WIDE else STATE_GRID_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            contentPadding = PaddingValues(bottom = edge),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = content.bottomInset)
                .verticalEdgeFade(gridState, fadeHeight = Dimens.spacingSm + Dimens.spacingXs, top = false)
        ) {
            items(states, key = { it.slotNumber }) { quickState ->
                StateTile(
                    quickState = quickState,
                    armed = armed.isArmed(quickState.slotNumber),
                    onTap = {
                        if (armed.isArmed(quickState.slotNumber)) {
                            armed = ArmedState()
                            content.actions.onLoadState(quickState)
                        } else {
                            armed = ArmedState(quickState.slotNumber, now() + CONFIRM_WINDOW_MS)
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun TrophiesSection(content: DashboardContent) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.fillMaxSize().padding(top = Dimens.spacingLg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.dual_dashboard_rail_trophies),
                style = MaterialTheme.typography.headlineSmall,
                color = theme.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    R.string.dual_dashboard_achievements_progress,
                    content.achievementEarned,
                    content.achievementTotal
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ALauncherColors.TrophyAmber
            )
        }
        ProgressBar(
            fraction = content.achievementFraction(),
            modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        )
        val listState = rememberLazyListState()
        AchievementList(
            achievements = content.achievements,
            focusIndex = -1,
            unlockedHeadingRes = R.string.ingame_achievements_unlocked_heading,
            lockedHeadingRes = R.string.ingame_achievements_locked_heading,
            emptyTextRes = R.string.ingame_achievements_empty,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = content.bottomInset)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingSm + Dimens.spacingXs, top = false),
            contentPadding = PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                bottom = Dimens.spacingLg
            ),
            listState = listState
        )
    }
}

@Composable
private fun SessionHeader(content: DashboardContent, showsCover: Boolean) {
    val theme = LocalArgosyTheme.current
    val state = content.state
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showsCover) {
            AsyncImage(
                model = rememberFileImageModel(state.coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(SESSION_COVER_WIDTH)
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (!showsCover) {
                Text(
                    text = stringResource(R.string.dual_companion_session_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim
                )
            } else {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SessionStatusLine(content)
            val sessionMinutes = (content.sessionMillis / 60_000).toInt()
            Text(
                text = stringResource(
                    R.string.dual_dashboard_total_play,
                    playTimeLabel(state.playTimeMinutes + sessionMinutes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
            if (sessionMinutes > 0) {
                Text(
                    text = stringResource(R.string.dual_dashboard_session_play, playTimeLabel(sessionMinutes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
        HeaderActions(content, Modifier.weight(HEADER_ACTIONS_WEIGHT))
    }
}

@Composable
private fun SessionStatusLine(content: DashboardContent) {
    val theme = LocalArgosyTheme.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val state = content.state
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.iconSm)
                .clip(CircleShape)
                .background(if (state.isDirty) semantic.warning else semantic.success),
            contentAlignment = Alignment.Center
        ) {
            if (state.isDirty) {
                Box(
                    modifier = Modifier
                        .size(Dimens.spacingXs)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            } else {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Dimens.iconXs)
                )
            }
        }
        Text(
            text = if (state.isDirty) {
                stringResource(R.string.dual_companion_saves_dirty)
            } else {
                stringResource(R.string.dual_companion_saves_synced)
            },
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun ActionRow(content: DashboardContent) {
    if (!content.state.quickActionsAvailable) return
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (content.statesAllowed) SaveButton(content, Modifier.weight(1f))
        ScreenshotButton(content, Modifier.weight(1f))
    }
}

@Composable
private fun HeaderActions(content: DashboardContent, modifier: Modifier) {
    if (!content.state.quickActionsAvailable) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (content.statesAllowed) SaveButton(content, Modifier.fillMaxWidth())
        ScreenshotButton(content, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecentStates(content: DashboardContent) {
    if (!content.statesAllowed) return
    val recent = content.controls.quickStates.take(RECENT_STATE_COUNT)
    if (recent.isEmpty()) return
    val theme = LocalArgosyTheme.current
    var armed by remember { mutableStateOf(ArmedState()) }
    ClearAt(armed.until) { armed = ArmedState() }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Row {
            Text(
                text = stringResource(R.string.dual_dashboard_recent_states),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.dual_dashboard_states_tap_twice),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            recent.forEach { quickState ->
                key(quickState.slotNumber) {
                    StateTile(
                        quickState = quickState,
                        armed = armed.isArmed(quickState.slotNumber),
                        latest = quickState == recent.first(),
                        modifier = Modifier.weight(1f),
                        onTap = {
                            if (armed.isArmed(quickState.slotNumber)) {
                                armed = ArmedState()
                                content.actions.onLoadState(quickState)
                            } else {
                                armed = ArmedState(quickState.slotNumber, now() + CONFIRM_WINDOW_MS)
                            }
                        }
                    )
                }
            }
            repeat(RECENT_STATE_COUNT - recent.size) { Box(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun SaveButton(content: DashboardContent, modifier: Modifier) {
    ActionButton(
        label = stringResource(R.string.dual_companion_quick_action_save_state),
        primary = true,
        modifier = modifier,
        onTap = content.actions.onQuickSave
    )
}

@Composable
private fun ScreenshotButton(content: DashboardContent, modifier: Modifier) {
    ActionButton(
        label = stringResource(R.string.dual_companion_quick_action_screenshot),
        modifier = modifier,
        onTap = content.actions.onScreenshot
    )
}

@Composable
private fun StateTile(
    quickState: DashboardQuickState,
    armed: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    latest: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val request = remember(quickState.screenshotPath, quickState.savedAtMillis) {
        quickState.screenshotPath?.let { path ->
            ImageRequest.Builder(context)
                .data(File(path))
                .memoryCacheKey("$path@${quickState.savedAtMillis}")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
    Column(
        modifier = modifier.touchOnly(onTap),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(STATE_THUMB_ASPECT)
                .clip(shape)
                .background(theme.surfaceRaised)
                .then(
                    if (armed) Modifier.border(Dimens.borderMedium, theme.focusAccent, shape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (request != null) {
                ScreenshotImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (latest && !armed) {
                Text(
                    text = stringResource(R.string.dual_dashboard_state_latest),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Dimens.spacingXs)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(theme.focusAccent)
                        .padding(horizontal = Dimens.spacingXs)
                )
            }
            if (armed) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = CARD_ALPHA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dual_dashboard_state_tap_to_load),
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.focusAccent
                    )
                }
            }
        }
        val nowMillis = System.currentTimeMillis()
        Text(
            text = if (nowMillis - quickState.savedAtMillis < DateUtils.MINUTE_IN_MILLIS) {
                stringResource(R.string.dual_dashboard_state_just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(
                    quickState.savedAtMillis,
                    nowMillis,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            },
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
            maxLines = 1
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.documentCards(content: DashboardContent) {
    val state = content.state
    if (state.manual == null && state.walkthrough == null) return
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            if (state.manual != null) ManualCard(content, Modifier.weight(1f))
            if (state.walkthrough != null) GuideCard(content, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ManualCard(content: DashboardContent, modifier: Modifier) {
    DocumentCard(
        title = stringResource(R.string.dual_dashboard_rail_manual),
        detail = content.state.manualLastPage?.let {
            stringResource(R.string.dual_dashboard_manual_resume, it + 1)
        } ?: stringResource(R.string.dual_dashboard_document_open),
        modifier = modifier,
        onTap = { content.onOpenSection(DashboardSection.MANUAL) }
    )
}

@Composable
private fun GuideCard(content: DashboardContent, modifier: Modifier) {
    DocumentCard(
        title = stringResource(R.string.dual_dashboard_rail_guide),
        detail = content.state.walkthroughProgress?.takeIf { it > 0f }?.let {
            stringResource(R.string.dual_dashboard_guide_progress, readPercent(it))
        } ?: stringResource(R.string.dual_dashboard_document_open),
        modifier = modifier,
        onTap = { content.onOpenSection(DashboardSection.GUIDE) }
    )
}

@Composable
private fun DocumentCard(title: String, detail: String, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceBase.copy(alpha = CARD_ALPHA))
            .touchOnly(onTap)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = theme.textPrimary
        )
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = theme.textDim)
    }
}

@Composable
private fun AchievementCard(content: DashboardContent) {
    if (content.achievementTotal <= 0) return
    val theme = LocalArgosyTheme.current
    val latest = content.achievements.latestUnlocked()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceBase.copy(alpha = CARD_ALPHA))
            .touchOnly { content.onOpenSection(DashboardSection.TROPHIES) }
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            latest?.badgeUrl?.let { badge ->
                AsyncImage(
                    model = rememberFileImageModel(badge),
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconXl).clip(RoundedCornerShape(Dimens.radiusSm))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (latest != null) {
                        stringResource(R.string.dual_dashboard_latest_achievement)
                    } else {
                        stringResource(R.string.dual_dashboard_achievements_label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
                latest?.let {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.dual_dashboard_achievements_progress,
                    content.achievementEarned,
                    content.achievementTotal
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ALauncherColors.TrophyAmber
            )
        }
        ProgressBar(fraction = content.achievementFraction())
    }
}

@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PROGRESS_HEIGHT)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.surfaceRaised)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(ALauncherColors.TrophyAmber)
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val theme = LocalArgosyTheme.current
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .heightIn(min = DimensionTokens.Layout.buttonHeight.dp + Dimens.spacingSm)
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(
                when {
                    !enabled -> theme.surfaceRaised.copy(alpha = CARD_ALPHA)
                    primary -> theme.focusAccent
                    else -> theme.surfaceRaised
                }
            )
            .then(if (enabled) Modifier.touchOnly(onTap) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                !enabled -> theme.textMute
                primary -> colors.onPrimary
                else -> theme.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun playTimeLabel(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours > 0 && mins > 0 -> stringResource(R.string.dual_companion_play_time_hours_minutes, hours, mins)
        hours > 0 -> stringResource(R.string.dual_companion_play_time_hours, hours)
        mins > 0 -> stringResource(R.string.dual_companion_play_time_minutes, mins)
        else -> stringResource(R.string.dual_companion_play_time_zero)
    }
}

internal fun readPercent(fraction: Float): Int =
    kotlin.math.ceil(fraction * 100).toInt().coerceIn(1, 100)

internal fun List<com.nendo.argosy.core.game.AchievementUi>.latestUnlocked() =
    filter { it.isUnlocked }.maxByOrNull { it.unlockedAtMillis ?: 0L }

private data class ArmedState(val slotNumber: Int = -1, val until: Long = 0L) {
    fun isArmed(slot: Int): Boolean = slot == slotNumber && now() < until
}

private fun DashboardContent.achievementFraction(): Float =
    if (achievementTotal > 0) achievementEarned.toFloat() / achievementTotal else 0f

private fun now(): Long = android.os.SystemClock.elapsedRealtime()

@Composable
internal fun ClearAt(until: Long, onExpired: () -> Unit) {
    LaunchedEffect(until) {
        if (until <= 0L) return@LaunchedEffect
        delay((until - now()).coerceAtLeast(0L))
        onExpired()
    }
}
