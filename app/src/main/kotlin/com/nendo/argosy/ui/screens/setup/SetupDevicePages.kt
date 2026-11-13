package com.nendo.argosy.ui.screens.setup

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.HomeLayoutSelectorRow
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import com.nendo.argosy.ui.screens.settings.sections.ScreenCard
import com.nendo.argosy.ui.screens.settings.sections.ScreenSetupIdentity
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckExit
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckFocus
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckKeyCapture
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckPanel
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckRows
import com.nendo.argosy.ui.screens.setup.components.ControllerDiagram
import com.nendo.argosy.ui.screens.setup.components.ControllerDiagramSize
import com.nendo.argosy.ui.screens.setup.components.LibraryLookPreview
import com.nendo.argosy.ui.screens.setup.components.PadReading
import com.nendo.argosy.ui.screens.setup.components.SetupAction
import com.nendo.argosy.ui.screens.setup.components.SetupChoiceRow
import com.nendo.argosy.ui.screens.setup.components.SetupCompanionBody
import com.nendo.argosy.ui.screens.setup.components.SetupCompanionState
import com.nendo.argosy.ui.screens.setup.components.SetupScreenBlock
import com.nendo.argosy.ui.screens.setup.components.SetupStepBody
import com.nendo.argosy.ui.screens.setup.components.screenRoleLabelRes
import com.nendo.argosy.ui.theme.AccentHue
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.ThemeState
import com.nendo.argosy.ui.theme.argosyThemeTokens
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.rememberArgosyPalette
import com.nendo.argosy.ui.util.clickableNoFocus

private val XBOX_GLYPHS = ButtonGlyphSwaps(ab = false, xy = false, startSelect = false)
private val NINTENDO_GLYPHS = ButtonGlyphSwaps(ab = true, xy = true, startSelect = false)

fun companionFor(state: SetupUiState, swaps: ButtonGlyphSwaps): SetupCompanionState {
    val focus = focusedTarget(state)
    val lead = leadFor(state)
    val body = when (state.page) {
        SetupPage.LIBRARY -> if (state.signIn.view == LibraryView.CHOICE) {
            SetupCompanionBody.LibraryContext
        } else {
            SetupCompanionBody.Brand
        }
        SetupPage.PERMISSIONS -> {
            val permission = when (focus) {
                is SetupTarget.Permission -> focus.permission
                is SetupTarget.AccessibilityStep -> ArgosyPermission.USAGE_ACCESS
                else -> SETUP_REQUIRED_PERMISSIONS.first()
            }
            SetupCompanionBody.Permission(permission, state.access.availabilityOf(permission))
        }
        SetupPage.ACCESSIBILITY -> SetupCompanionBody.Accessibility(lead.purpose, state.access)
        SetupPage.FOLDERS -> {
            val kind = (focus as? SetupTarget.RomFolder)?.kind ?: state.folders.selectedRom
            val path = state.folders.pathOf(kind)
            if (path == null) {
                SetupCompanionBody.Brand
            } else {
                val space = state.folders.space[path]
                SetupCompanionBody.Folder(path, space?.freeBytes, space?.totalBytes, space?.exists ?: true)
            }
        }
        SetupPage.SAVE_SYNC -> SetupCompanionBody.SyncDiagram(state.signIn.serverName)
        SetupPage.PLATFORMS -> SetupCompanionBody.Platforms(
            selected = state.platforms.all.count { it.syncEnabled },
            total = state.platforms.all.size
        )
        SetupPage.SCREENS -> SetupCompanionBody.Screens(
            setupScreens(state).sortedBy { it.number }.map { SetupScreenBlock(it.number, it.role) }
        )
        SetupPage.CONTROLLER -> SetupCompanionBody.Controller(
            reading = PadReading(),
            swaps = swaps,
            swapped = emptySet(),
            showsLastPress = false,
            testing = false
        )
        SetupPage.BUTTON_CHECK -> SetupCompanionBody.Controller(
            reading = state.buttonCheck.reading,
            swaps = swaps,
            swapped = state.buttonCheck.swappedButtons,
            showsLastPress = true,
            testing = state.buttonCheck.testing
        )
        SetupPage.THEME, SetupPage.HOME_LAYOUT -> SetupCompanionBody.HomePreview(state.look.homeLayout)
        SetupPage.LIBRARY_LOOK -> SetupCompanionBody.LibraryPreview(state.look.libraryLayout, state.look.boxArtShape)
        SetupPage.DONE -> SetupCompanionBody.Done(
            serverName = state.signIn.serverName,
            gameCount = state.signIn.gameCount,
            romPath = state.folders.selectedRomPath,
            saveSyncOn = state.saveSyncOn,
            cores = state.cores
        )
        SetupPage.WELCOME, SetupPage.DEFAULT_HOME -> SetupCompanionBody.Brand
    }
    val title = if (state.page == SetupPage.SCREENS && screensUseSwapRow(state)) {
        R.string.setup_screens_companion_title
    } else {
        lead.title
    }
    return SetupCompanionState(title = title, body = body)
}

@Composable
internal fun DefaultHomePage(state: SetupUiState, viewModel: SetupViewModel) {
    val theme = LocalArgosyTheme.current
    SetupPageBody(state = state, viewModel = viewModel) {
        Text(
            text = stringResource(if (state.facts.defaultHome) R.string.setup_home_body_set else R.string.setup_home_body),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary,
        )
        if (!state.facts.defaultHome) {
            Text(
                text = stringResource(R.string.setup_home_decline_note),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                modifier = Modifier.padding(top = Dimens.spacingMd),
            )
        }
    }
}

@Composable
internal fun ScreensPage(state: SetupUiState, viewModel: SetupViewModel) {
    val screens = setupScreens(state)
    val focus = focusedTarget(state)
    val context = LocalContext.current
    val hereDisplayId = remember(context) { ContextCompat.getDisplayOrDefault(context).displayId }
    val swapRow = screensUseSwapRow(state)
    val companionShowsNumbers = swapRow && setupShownOnCompanion()
    DisposableEffect(hereDisplayId, companionShowsNumbers) {
        val manager = DualScreenManagerHolder.instance.takeUnless { companionShowsNumbers }
        manager?.showScreenNumbers(inContentDisplayId = hereDisplayId)
        onDispose { manager?.hideScreenNumbers() }
    }
    val hereNumber = state.screens.find { it.displayId == hereDisplayId }?.number
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        leadTrailing = hereNumber?.let { { ScreenSetupIdentity(number = it) } },
    ) {
        if (swapRow) {
            SetupChoiceRow(
                icon = Icons.Default.SwapHoriz,
                title = stringResource(R.string.setup_screens_swap_row),
                subtitle = null,
                selected = false,
                focused = focus == SetupTarget.SwapScreens,
                onClick = { viewModel.tap(SetupTarget.SwapScreens) },
            )
        } else {
            ScreenSketch(screens = screens, focus = focus, viewModel = viewModel)
        }
    }
}

@Composable
private fun ColumnScope.ScreenSketch(screens: List<ScreenAssignment>, focus: SetupTarget?, viewModel: SetupViewModel) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        val widestPx = screens.maxOfOrNull { it.widthPx }?.coerceAtLeast(1) ?: 1
        val stackedPx = screens.sumOf { it.heightPx }.coerceAtLeast(1)
        val gaps = Dimens.spacingSm * (screens.size - 1).coerceAtLeast(0)
        val perPixel = minOf(maxWidth / widestPx.toFloat(), (maxHeight - gaps) / stackedPx.toFloat())
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            screens.forEach { screen ->
                ScreenCard(
                    screen = screen,
                    perPixel = perPixel,
                    isFocused = focus == SetupTarget.Screen(screen.key),
                    onClick = { viewModel.tap(SetupTarget.Screen(screen.key)) },
                    roleLabelRes = screenRoleLabelRes(screen.role),
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.setup_screens_line),
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(top = Dimens.spacingSm),
    )
}

@Composable
internal fun ControllerPage(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    SetupPageBody(state = state, viewModel = viewModel) {
        Crossfade(
            targetState = state.gamepadConnected,
            animationSpec = if (reduced) snap() else tween(Motion.durationContent),
            label = "setup-controller-view",
        ) { connected ->
            if (connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                ) {
                    listOf(false, true).forEach { nintendo ->
                        LayoutChoiceCard(
                            nintendo = nintendo,
                            selected = state.controllerNintendo == nintendo,
                            focused = focus == SetupTarget.ControllerLayoutChoice(nintendo),
                            onClick = { viewModel.tap(SetupTarget.ControllerLayoutChoice(nintendo)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = LocalArgosyTheme.current.textDim,
                        modifier = Modifier.size(Dimens.iconXl),
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutChoiceCard(
    nintendo: Boolean,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    Column(
        modifier = modifier
            .clip(shape)
            .background(theme.surfaceRaised)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, selected = selected, shape = shape)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
    ) {
        ControllerDiagram(
            swaps = if (nintendo) NINTENDO_GLYPHS else XBOX_GLYPHS,
            variant = ControllerDiagramSize.MINI,
        )
        Text(
            text = stringResource(if (nintendo) R.string.setup_controller_nintendo else R.string.setup_controller_xbox),
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) theme.focusAccent else theme.textPrimary,
        )
        Text(
            text = stringResource(
                if (nintendo) R.string.setup_controller_nintendo_detail else R.string.setup_controller_xbox_detail
            ),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
        )
    }
}

@Composable
internal fun ButtonCheckPage(state: SetupUiState, viewModel: SetupViewModel) {
    val check = state.buttonCheck
    if (!setupShownOnCompanion()) {
        SetupPageBody(state = state, viewModel = viewModel) {
            ButtonCheckPanel(
                state = check,
                controller = viewModel.buttonCheck,
                continueLabel = R.string.setup_check_button_continue,
                backLabel = R.string.setup_check_button_back,
                onExit = viewModel::exitButtonCheck,
            )
        }
        return
    }
    ButtonCheckKeyCapture(state = check, controller = viewModel.buttonCheck)
    val lead = leadFor(state)
    SetupStepBody(
        lead = if (check.testing) lead.copy(purpose = R.string.setup_check_purpose_testing) else lead,
        actions = check.actions.map { exit ->
            SetupAction(
                label = if (exit == ButtonCheckFocus.BACK) R.string.setup_check_button_back else R.string.setup_check_button_continue,
                primary = exit == ButtonCheckFocus.CONTINUE,
                focused = check.focus == exit,
            )
        },
        onAction = { index ->
            val exit = if (check.actions.getOrNull(index) == ButtonCheckFocus.BACK) ButtonCheckExit.BACK else ButtonCheckExit.CONTINUE
            viewModel.exitButtonCheck(exit)
        },
    ) {
        ButtonCheckRows(state = check, controller = viewModel.buttonCheck)
    }
}

@Composable
private fun ColumnScope.LookPreview(content: @Composable () -> Unit) {
    if (setupShownOnCompanion()) return
    val maxHeight = (DimensionTokens.Layout.setupPreviewMaxHeight * LocalUiScale.current.scale).dp
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .padding(bottom = Dimens.spacingMd)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ThemedLookPreview(themeMode: ThemeMode, primaryColor: Int?, content: @Composable () -> Unit) {
    val previewState = remember(themeMode, primaryColor) {
        ThemeState(themeMode = themeMode, primaryColor = primaryColor)
    }
    val palette = rememberArgosyPalette(previewState)
    CompositionLocalProvider(LocalArgosyTheme provides argosyThemeTokens(palette), content = content)
}

@Composable
private fun LookLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(top = Dimens.spacingSm, bottom = Dimens.spacingXs),
    )
}

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.setup_look_theme_light
    ThemeMode.DARK -> R.string.setup_look_theme_dark
    ThemeMode.SYSTEM -> R.string.setup_look_theme_system
}

private fun libraryLayoutLabelRes(layout: LibraryLayout): Int = when (layout) {
    LibraryLayout.GRID -> R.string.setup_look_library_grid
    LibraryLayout.LIST -> R.string.setup_look_library_list
}

private fun boxArtShapeLabelRes(shape: BoxArtShape): Int = when (shape) {
    BoxArtShape.TALL -> R.string.setup_look_shape_tall
    BoxArtShape.SQUARE -> R.string.setup_look_shape_square
    else -> R.string.setup_look_shape_standard
}

@Composable
internal fun ThemePage(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    val look = state.look
    SetupPageBody(state = state, viewModel = viewModel) {
        LookPreview {
            ThemedLookPreview(themeMode = look.themeMode, primaryColor = look.primaryColor) {
                HomeLayoutPreview(settings = look.homeLayout, animate = false)
            }
        }
        LookLabel(stringResource(R.string.setup_look_theme_mode_label))
        SegmentedControl(
            options = ThemeMode.entries.map { stringResource(themeModeLabelRes(it)) },
            selectedIndex = ThemeMode.entries.indexOf(look.themeMode),
            onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
            focused = focus == SetupTarget.ThemeMode,
            modifier = Modifier.fillMaxWidth(),
        )
        LookLabel(stringResource(R.string.setup_look_accent_label))
        AccentSwatches(
            selected = look.primaryColor,
            focused = focus == SetupTarget.Accent,
            onSelect = viewModel::setAccent,
        )
    }
}

@Composable
private fun AccentSwatches(selected: Int?, focused: Boolean, onSelect: (Int?) -> Unit) {
    val theme = LocalArgosyTheme.current
    val rowShape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.ListRow, shape = rowShape)
            .padding(Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentHue.SWATCHES.forEach { color ->
            val active = color == selected
            Box(
                modifier = Modifier
                    .size(Dimens.iconLg)
                    .clip(CircleShape)
                    .background(if (color == null) theme.surfaceElevated else Color(color))
                    .border(
                        width = if (active) Dimens.borderMedium else Dimens.borderThin,
                        color = if (active) Color.White else theme.hairlineHigh,
                        shape = CircleShape,
                    )
                    .clickableNoFocus { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (color == null) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = stringResource(R.string.setup_look_accent_default_desc),
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconSm),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeLayoutPage(state: SetupUiState, viewModel: SetupViewModel) {
    val look = state.look
    SetupPageBody(state = state, viewModel = viewModel) {
        LookPreview { HomeLayoutPreview(settings = look.homeLayout) }
        HomeLayoutSelectorRow(
            selected = look.homeLayout.selected,
            isFocused = focusedTarget(state) == SetupTarget.HomeLayout,
            onSelect = viewModel::setHomeLayout,
        )
    }
}

@Composable
internal fun LibraryLookPage(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    val look = state.look
    SetupPageBody(state = state, viewModel = viewModel) {
        LookPreview { LibraryLookPreview(layout = look.libraryLayout, shape = look.boxArtShape, modifier = Modifier.fillMaxWidth()) }
        LookLabel(stringResource(R.string.setup_look_library_layout_label))
        SegmentedControl(
            options = LibraryLayout.entries.map { stringResource(libraryLayoutLabelRes(it)) },
            selectedIndex = LibraryLayout.entries.indexOf(look.libraryLayout),
            onSelect = { viewModel.setLibraryLayout(LibraryLayout.entries[it]) },
            focused = focus == SetupTarget.LibraryLayout,
            modifier = Modifier.fillMaxWidth(),
        )
        LookLabel(stringResource(R.string.setup_look_shape_label))
        SegmentedControl(
            options = SETUP_BOX_ART_SHAPES.map { stringResource(boxArtShapeLabelRes(it)) },
            selectedIndex = SETUP_BOX_ART_SHAPES.indexOf(look.boxArtShape).coerceAtLeast(0),
            onSelect = { viewModel.setBoxArtShape(SETUP_BOX_ART_SHAPES[it]) },
            focused = focus == SetupTarget.BoxArtShape,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.setup_look_more_in_settings),
            style = MaterialTheme.typography.bodySmall,
            color = LocalArgosyTheme.current.textDim,
            modifier = Modifier.padding(top = Dimens.spacingMd),
        )
    }
}
