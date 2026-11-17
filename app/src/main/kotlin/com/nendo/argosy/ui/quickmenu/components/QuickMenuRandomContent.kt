package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.quickmenu.QuickMenuFormFactor
import com.nendo.argosy.ui.quickmenu.QuickMenuGameUi
import com.nendo.argosy.ui.quickmenu.RANDOM_HISTORY_LIMIT
import com.nendo.argosy.ui.quickmenu.RandomState
import com.nendo.argosy.ui.quickmenu.RandomStop
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import java.text.NumberFormat

private val RandomTokens = ComponentDefaults.QuickMenuRandom

/**
 * The Random section. On [QuickMenuFormFactor.DUAL_SCREEN] the presentation screen carries the
 * cover, so the panel holds the controls alone. [showCursor] draws focus and [showGlyphs] the
 * button glyphs; both are off in touch mode.
 */
@Composable
fun QuickMenuRandomContent(
    random: RandomState,
    formFactor: QuickMenuFormFactor,
    showCursor: Boolean,
    showGlyphs: Boolean,
    onStop: (RandomStop) -> Unit,
    onHistoryTap: (Int) -> Unit,
    onCyclePlatform: (Int) -> Unit,
    onCoverTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controls = RandomControls(random, showCursor, showGlyphs, onStop, onHistoryTap, onCyclePlatform)
    when (formFactor) {
        QuickMenuFormFactor.DUAL_SCREEN -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            RandomHeading(random = random, showDetails = false)
            controls.Actions(stacked = false)
            controls.History(eyebrow = true)
            controls.Pool(sideBySide = false)
        }
        QuickMenuFormFactor.LANDSCAPE -> BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
        ) {
            val coverMaxWidth = maxWidth * RandomTokens.coverWidthMaxRatio
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
            ) {
                RollCover(
                    game = random.current,
                    onTap = onCoverTap,
                    modifier = Modifier
                        .widthIn(max = coverMaxWidth)
                        .fillMaxHeight()
                        .aspectRatio(RandomTokens.coverAspectRatio, matchHeightConstraintsFirst = true)
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                ) {
                    RandomHeading(random = random, showDetails = true)
                    controls.Actions(stacked = false)
                    controls.History(eyebrow = false)
                    controls.Pool(sideBySide = true)
                }
            }
        }
        QuickMenuFormFactor.PORTRAIT -> Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RollCover(
                game = random.current,
                onTap = onCoverTap,
                modifier = Modifier
                    .fillMaxWidth(RandomTokens.portraitCoverWidthRatio)
                    .aspectRatio(RandomTokens.coverAspectRatio)
            )
            RandomHeading(random = random, showDetails = true, centered = true)
            controls.Actions(stacked = true)
            controls.History(eyebrow = true)
            controls.Pool(sideBySide = false)
        }
    }
}

private class RandomControls(
    val random: RandomState,
    val showCursor: Boolean,
    val showGlyphs: Boolean,
    val onStop: (RandomStop) -> Unit,
    val onHistoryTap: (Int) -> Unit,
    val onCyclePlatform: (Int) -> Unit
) {
    fun focused(stop: RandomStop): Boolean = showCursor && random.focus == stop

    @Composable
    fun Actions(stacked: Boolean) {
        val game = random.current
        val isRunning = game != null && LocalRunningGames.current.isRunning(game.id)
        val playLabel = stringResource(
            when {
                isRunning -> R.string.ui_quick_menu_random_action_resume_game
                game?.needsInstall == true -> R.string.ui_quick_menu_random_action_install
                game == null || game.isDownloaded -> R.string.ui_quick_menu_random_action_play
                else -> R.string.ui_quick_menu_random_action_download
            }
        )
        val favoriteLabel = stringResource(
            if (game?.isFavorite == true) R.string.ui_quick_menu_random_action_favorited
            else R.string.ui_quick_menu_random_action_favorite
        )
        val play: @Composable (Modifier) -> Unit = { modifier ->
            ActionButton(
                label = playLabel,
                onClick = { onStop(RandomStop.PLAY) },
                focused = focused(RandomStop.PLAY),
                primary = true,
                enabled = random.isEnabled(RandomStop.PLAY),
                modifier = modifier
            )
        }
        val secondary: @Composable (Modifier) -> Unit = { modifier ->
            ActionButton(
                label = stringResource(R.string.ui_quick_menu_random_action_reroll),
                onClick = { onStop(RandomStop.ROLL) },
                focused = focused(RandomStop.ROLL),
                enabled = random.isEnabled(RandomStop.ROLL),
                modifier = modifier
            )
        }
        val details: @Composable (Modifier) -> Unit = { modifier ->
            ActionButton(
                label = stringResource(R.string.ui_quick_menu_random_action_details),
                onClick = { onStop(RandomStop.DETAILS) },
                focused = focused(RandomStop.DETAILS),
                enabled = random.isEnabled(RandomStop.DETAILS),
                glyph = InputButton.X.takeIf { showGlyphs },
                modifier = modifier
            )
        }
        val favorite: @Composable (Modifier) -> Unit = { modifier ->
            ActionButton(
                label = favoriteLabel,
                onClick = { onStop(RandomStop.FAVORITE) },
                focused = focused(RandomStop.FAVORITE),
                enabled = random.isEnabled(RandomStop.FAVORITE),
                glyph = InputButton.Y.takeIf { showGlyphs },
                modifier = modifier
            )
        }
        if (stacked) {
            play(Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                secondary(Modifier.weight(1f))
                details(Modifier.weight(1f))
                favorite(Modifier.weight(1f))
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                play(Modifier.weight(1f))
                secondary(Modifier.weight(1f))
                details(Modifier.weight(1f))
                favorite(Modifier.weight(1f))
            }
        }
    }

    @Composable
    fun History(eyebrow: Boolean) {
        val theme = LocalArgosyTheme.current
        val onHistoryRow = showCursor && random.focus == RandomStop.HISTORY
        val blanks = RANDOM_HISTORY_LIMIT - random.rolls.size
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            if (eyebrow) {
                Text(
                    text = stringResource(R.string.ui_quick_menu_random_history).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                repeat(RANDOM_HISTORY_LIMIT) { slot ->
                    val index = slot - blanks
                    val roll = random.rolls.getOrNull(index)
                    val shape = RoundedCornerShape(Dimens.radiusMd)
                    val tileModifier = Modifier.weight(1f).aspectRatio(RandomTokens.coverAspectRatio)
                    if (roll == null) {
                        Box(modifier = tileModifier.clip(shape).background(theme.surfaceElevated))
                        return@repeat
                    }
                    val ringed = onHistoryRow && index == random.historyFocus
                    val isPick = index == random.rollIndex
                    QuickMenuCover(
                        path = roll.coverPath,
                        title = roll.title,
                        modifier = tileModifier
                            .alpha(if (isPick || ringed) 1f else ComponentDefaults.QuickMenu.historyTileAlpha)
                            .argosyFocusIndicators(focused = ringed, indicators = FocusIndicators(halo = true, ring = true), shape = shape)
                            .clickableNoFocus { onHistoryTap(index) }
                    )
                }
            }
        }
    }

    @Composable
    fun Pool(sideBySide: Boolean) {
        val platform: @Composable (Modifier) -> Unit = { modifier ->
            PoolRow(
                label = stringResource(R.string.ui_quick_menu_random_platform_label),
                focused = focused(RandomStop.PLATFORM),
                onTap = { onStop(RandomStop.PLATFORM) },
                modifier = modifier
            ) {
                EnumValueControl(
                    value = random.platformName ?: stringResource(R.string.ui_quick_menu_random_platform_all),
                    focused = focused(RandomStop.PLATFORM),
                    onPrev = { onCyclePlatform(-1) },
                    onNext = { onCyclePlatform(1) },
                    onOpen = { onStop(RandomStop.PLATFORM) },
                    selectedIndex = random.platformIndex,
                    optionCount = random.platforms.size + 1
                )
            }
        }
        val installed: @Composable (Modifier) -> Unit = { modifier ->
            PoolRow(
                label = stringResource(R.string.ui_quick_menu_random_installed_only),
                focused = focused(RandomStop.INSTALLED),
                onTap = { onStop(RandomStop.INSTALLED) },
                modifier = modifier
            ) {
                ArgosyToggle(
                    checked = random.installedOnly,
                    onToggle = { onStop(RandomStop.INSTALLED) },
                    focused = focused(RandomStop.INSTALLED)
                )
            }
        }
        if (sideBySide) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                platform(Modifier.weight(1f))
                installed(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                platform(Modifier.fillMaxWidth())
                installed(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PoolRow(
    label: String,
    focused: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = modifier
            .height(Dimens.menuRowHeightLg)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators(fill = true, ring = true), shape = shape)
            .clickableNoFocus(onClick = onTap)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) theme.focusAccent else theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        control()
    }
}

@Composable
private fun RollCover(game: QuickMenuGameUi?, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    AnimatedContent(
        targetState = game,
        contentKey = { it?.id },
        transitionSpec = {
            val fade = tween<Float>(Motion.durationPage)
            if (reduced) {
                fadeIn(fade) togetherWith fadeOut(fade)
            } else {
                val slide = tween<IntOffset>(Motion.durationPage, easing = Motion.argosyEase)
                (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith
                    (slideOutVertically(slide) { -it } + fadeOut(fade))
            }
        },
        modifier = modifier.clip(RoundedCornerShape(Dimens.radiusMd)),
        label = "randomRollCover"
    ) { shown ->
        if (shown == null) {
            Box(modifier = Modifier.fillMaxSize().background(LocalArgosyTheme.current.surfaceElevated))
            return@AnimatedContent
        }
        QuickMenuCover(
            path = shown.coverPath,
            title = shown.title,
            modifier = Modifier
                .fillMaxSize()
                .clickableNoFocus(onClick = onTap)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomHeading(random: RandomState, showDetails: Boolean, centered: Boolean = false) {
    val theme = LocalArgosyTheme.current
    val game = random.current
    val align = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        val emptyMessage = when {
            !random.isPoolEmpty -> null
            random.isFiltered -> stringResource(R.string.ui_quick_menu_empty_random_pool)
            else -> stringResource(R.string.ui_quick_menu_empty_random)
        }
        Text(
            text = emptyMessage ?: game?.title.orEmpty(),
            style = if (emptyMessage != null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
            color = if (emptyMessage != null) theme.textDim else theme.textPrimary,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (showDetails && game != null && emptyMessage == null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, align),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                QuickMenuChip(
                    text = game.platformName ?: stringResource(R.string.ui_quick_menu_random_platform_unknown),
                    tone = ChipTone.ACCENT
                )
                if (game.isDownloaded) {
                    QuickMenuChip(stringResource(R.string.ui_quick_menu_random_downloaded), ChipTone.SUCCESS)
                }
            }
        }
        val meta = rollMeta(random, if (showDetails && emptyMessage == null) game else null)
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun rollMeta(random: RandomState, game: QuickMenuGameUi?): String? {
    val facts = game?.let { listOfNotNull(it.year?.toString(), it.genre).joinToString(" | ") }?.takeIf { it.isNotEmpty() }
    val roll = if (random.rollCount > 0) stringResource(R.string.ui_quick_menu_random_roll, random.rollNumber) else null
    val pool = random.poolSize?.takeIf { it > 0 }?.let { size ->
        pluralStringResource(R.plurals.ui_quick_menu_random_pool_count, size, NumberFormat.getIntegerInstance().format(size))
    }
    return listOfNotNull(facts, roll, pool).reduceOrNull { left, right ->
        stringResource(R.string.ui_quick_menu_random_roll_meta, left, right)
    }
}
