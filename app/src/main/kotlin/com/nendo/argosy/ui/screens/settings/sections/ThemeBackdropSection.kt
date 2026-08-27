package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.BackdropEdgeStyle
import com.nendo.argosy.data.preferences.BackdropMotion
import com.nendo.argosy.data.preferences.BackdropPreset
import com.nendo.argosy.data.preferences.BackdropVertexIcon
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.DirectionRingModal
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.roundToInt

internal data class ThemeBackdropLayoutState(
    val enabled: Boolean,
    val motion: BackdropMotion
) {
    companion object {
        fun from(state: SettingsUiState) = ThemeBackdropLayoutState(
            enabled = state.display.surfaceBackdrop.enabled,
            motion = state.display.surfaceBackdrop.motion
        )
    }
}

internal sealed class ThemeBackdropItem(
    val key: String,
    val section: String,
    val visibleWhen: (ThemeBackdropLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int, visibleWhen: (ThemeBackdropLayoutState) -> Boolean = { true }) :
        ThemeBackdropItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (ThemeBackdropLayoutState) -> Boolean = { true }) :
        ThemeBackdropItem(key, section, visibleWhen)

    data object Enabled : ThemeBackdropItem("backdropEnabled", "pattern")
    data object Preset : ThemeBackdropItem("backdropPreset", "pattern", { it.enabled })
    data object Density : ThemeBackdropItem("backdropDensity", "pattern", { it.enabled })
    data object Scatter : ThemeBackdropItem("backdropScatter", "pattern", { it.enabled })
    data object ScaleJitter : ThemeBackdropItem("backdropScaleJitter", "pattern", { it.enabled })
    data object Strength : ThemeBackdropItem("backdropStrength", "pattern", { it.enabled })
    data object EdgeLines : ThemeBackdropItem("backdropEdgeLines", "layers", { it.enabled })
    data object CornerIcons : ThemeBackdropItem("backdropCornerIcons", "layers", { it.enabled })
    data object Motion : ThemeBackdropItem("backdropMotion", "layers", { it.enabled })
    data object Speed : ThemeBackdropItem("backdropMotionSpeed", "layers", { it.enabled && it.motion != BackdropMotion.OFF })
    data object Direction : ThemeBackdropItem("backdropDriftAngle", "layers", { it.enabled && it.motion == BackdropMotion.DRIFT })
    data object Reshuffle : ThemeBackdropItem("backdropReshuffle", "pattern", { it.enabled })

    companion object {
        private val PatternHeader =
            Header("patternHeader", "pattern", R.string.settings_backdrop_section_pattern)
        private val LayersSpacer = SectionSpacer("layersSpacer", "layers", { it.enabled })
        private val LayersHeader =
            Header("layersHeader", "layers", R.string.settings_backdrop_section_layers, { it.enabled })

        val ALL: List<ThemeBackdropItem>
            get() = listOf(
                PatternHeader, Enabled, Preset, Density, Scatter, ScaleJitter, Strength, Reshuffle,
                LayersSpacer, LayersHeader, EdgeLines, CornerIcons, Motion, Speed, Direction
            )
    }
}

private val themeBackdropLayout = SettingsLayout<ThemeBackdropItem, ThemeBackdropLayoutState>(
    allItems = ThemeBackdropItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "pattern" -> R.string.settings_backdrop_section_pattern
            "layers" -> R.string.settings_backdrop_section_layers
            else -> null
        }
    }
)

private fun backdropPresetLabelRes(preset: BackdropPreset): Int = when (preset) {
    BackdropPreset.DOTS -> R.string.settings_backdrop_preset_dots
    BackdropPreset.SCANLINES -> R.string.settings_backdrop_preset_scanlines
    BackdropPreset.HEX -> R.string.settings_backdrop_preset_hex
    BackdropPreset.ICON_GRID -> R.string.settings_backdrop_preset_icon_grid
    BackdropPreset.PLATFORMS -> R.string.settings_backdrop_preset_platforms
}

private fun backdropEdgeStyleLabelRes(style: BackdropEdgeStyle): Int = when (style) {
    BackdropEdgeStyle.NONE -> R.string.settings_backdrop_edge_none
    BackdropEdgeStyle.SOLID -> R.string.settings_backdrop_edge_solid
    BackdropEdgeStyle.DASHED -> R.string.settings_backdrop_edge_dashed
    BackdropEdgeStyle.FADED -> R.string.settings_backdrop_edge_faded
    BackdropEdgeStyle.CONNECTIONS -> R.string.settings_backdrop_edge_connections
}

private fun backdropVertexIconLabelRes(icon: BackdropVertexIcon): Int = when (icon) {
    BackdropVertexIcon.NONE -> R.string.settings_backdrop_corner_none
    BackdropVertexIcon.DOTS -> R.string.settings_backdrop_corner_dots
    BackdropVertexIcon.PLUS -> R.string.settings_backdrop_corner_plus
    BackdropVertexIcon.DIAMOND -> R.string.settings_backdrop_corner_diamond
}

private fun backdropMotionLabelRes(motion: BackdropMotion): Int = when (motion) {
    BackdropMotion.OFF -> R.string.settings_backdrop_motion_off
    BackdropMotion.DRIFT -> R.string.settings_backdrop_motion_drift
    BackdropMotion.SWAY -> R.string.settings_backdrop_motion_sway
}

internal fun themeBackdropMaxFocusIndex(state: ThemeBackdropLayoutState): Int =
    themeBackdropLayout.maxFocusIndex(state)

internal fun themeBackdropItemAtFocusIndex(index: Int, state: ThemeBackdropLayoutState): ThemeBackdropItem? =
    themeBackdropLayout.itemAtFocusIndex(index, state)

internal fun themeBackdropSections(state: ThemeBackdropLayoutState) = themeBackdropLayout.buildSections(state)

@Composable
fun ThemeBackdropSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val config = uiState.display.surfaceBackdrop
    val context = LocalContext.current

    val layoutState = remember(config.enabled, config.motion) {
        ThemeBackdropLayoutState(enabled = config.enabled, motion = config.motion)
    }
    val visibleItems = remember(layoutState) { themeBackdropLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) {
        themeBackdropLayout.buildSections(layoutState, context)
    }

    fun isFocused(item: ThemeBackdropItem): Boolean =
        uiState.focusedIndex == themeBackdropLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: ThemeBackdropItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { themeBackdropLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is ThemeBackdropItem.SectionSpacer },
        isHeader = { it is ThemeBackdropItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is ThemeBackdropItem.Header -> ThemeBackdropSectionHeader(stringResource(item.titleRes))
            is ThemeBackdropItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeBackdropItem.Enabled -> SwitchPreference(
                title = stringResource(R.string.settings_backdrop_enabled_title),
                subtitle = stringResource(R.string.settings_backdrop_enabled_subtitle),
                isEnabled = config.enabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setBackdropEnabled(it) }
            )

            ThemeBackdropItem.Preset -> CyclePreference(
                title = stringResource(R.string.settings_backdrop_preset_title),
                value = stringResource(backdropPresetLabelRes(config.preset)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleBackdropPreset(1) },
                onPrev = { viewModel.cycleBackdropPreset(-1) },
                options = remember(context) {
                    BackdropPreset.entries.map { context.getString(backdropPresetLabelRes(it)) }
                },
                onSelect = { viewModel.setBackdropPreset(BackdropPreset.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeBackdropItem.Density -> SliderPreference(
                title = stringResource(R.string.settings_backdrop_density_title),
                value = config.cellSize,
                minValue = ComponentDefaults.SurfaceBackdrop.cellSizeMinDp,
                maxValue = ComponentDefaults.SurfaceBackdrop.cellSizeMaxDp,
                step = ComponentDefaults.SurfaceBackdrop.cellSizeStepDp,
                suffix = "dp",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustBackdropCellSize(it) }
            )

            ThemeBackdropItem.Scatter -> SliderPreference(
                title = stringResource(R.string.settings_backdrop_scatter_title),
                value = config.scatter,
                minValue = 0,
                maxValue = 200,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustBackdropScatter(it) }
            )

            ThemeBackdropItem.ScaleJitter -> SliderPreference(
                title = stringResource(R.string.settings_backdrop_scale_jitter_title),
                value = config.scaleJitter,
                minValue = 0,
                maxValue = 200,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustBackdropScaleJitter(it) }
            )

            ThemeBackdropItem.Strength -> SliderPreference(
                title = stringResource(R.string.settings_backdrop_strength_title),
                value = config.strength,
                minValue = 10,
                maxValue = 100,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustBackdropStrength(it) }
            )

            ThemeBackdropItem.EdgeLines -> CyclePreference(
                title = stringResource(R.string.settings_backdrop_edge_title),
                value = stringResource(backdropEdgeStyleLabelRes(config.edgeStyle)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleBackdropEdgeStyle(1) },
                onPrev = { viewModel.cycleBackdropEdgeStyle(-1) },
                options = remember(context) {
                    BackdropEdgeStyle.entries.map { context.getString(backdropEdgeStyleLabelRes(it)) }
                },
                onSelect = { viewModel.setBackdropEdgeStyle(BackdropEdgeStyle.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeBackdropItem.CornerIcons -> CyclePreference(
                title = stringResource(R.string.settings_backdrop_corner_title),
                value = stringResource(backdropVertexIconLabelRes(config.vertexIcons)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleBackdropVertexIcons(1) },
                onPrev = { viewModel.cycleBackdropVertexIcons(-1) },
                options = remember(context) {
                    BackdropVertexIcon.entries.map { context.getString(backdropVertexIconLabelRes(it)) }
                },
                onSelect = { viewModel.setBackdropVertexIcons(BackdropVertexIcon.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeBackdropItem.Motion -> CyclePreference(
                title = stringResource(R.string.settings_backdrop_motion_title),
                value = stringResource(backdropMotionLabelRes(config.motion)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleBackdropMotion(1) },
                onPrev = { viewModel.cycleBackdropMotion(-1) },
                options = remember(context) {
                    BackdropMotion.entries.map { context.getString(backdropMotionLabelRes(it)) }
                },
                onSelect = { viewModel.setBackdropMotion(BackdropMotion.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeBackdropItem.Speed -> SliderPreference(
                title = stringResource(R.string.settings_backdrop_speed_title),
                value = config.motionSpeed,
                minValue = 25,
                maxValue = 200,
                step = 25,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustBackdropMotionSpeed(it) }
            )

            ThemeBackdropItem.Direction -> DriftAnglePreference(
                angle = config.driftAngle,
                isFocused = isFocused(item),
                pickerRequestToken = pickerToken(item),
                onCommit = { viewModel.setBackdropDriftAngle(it) }
            )

            ThemeBackdropItem.Reshuffle -> ActionPreference(
                title = stringResource(R.string.settings_backdrop_reshuffle_title),
                subtitle = stringResource(R.string.settings_backdrop_reshuffle_subtitle),
                icon = Icons.Outlined.Shuffle,
                isFocused = isFocused(item),
                onClick = { viewModel.reshuffleBackdropSeed() }
            )

        }
    }
}

@Composable
private fun DriftAnglePreference(
    angle: Float,
    isFocused: Boolean,
    pickerRequestToken: Int,
    onCommit: (Float) -> Unit
) {
    var ringVisible by remember { mutableStateOf(false) }
    var consumedPickerToken by remember { mutableIntStateOf(pickerRequestToken) }
    LaunchedEffect(pickerRequestToken) {
        if (pickerRequestToken > consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
            ringVisible = true
        } else if (pickerRequestToken < consumedPickerToken) {
            consumedPickerToken = pickerRequestToken
        }
    }
    ActionPreference(
        title = stringResource(R.string.settings_backdrop_direction_title),
        subtitle = stringResource(
            R.string.settings_backdrop_direction_value,
            angle.roundToInt().mod(360)
        ),
        icon = Icons.Outlined.Explore,
        isFocused = isFocused,
        onClick = { ringVisible = true }
    )
    DirectionRingModal(
        angle = angle,
        visible = ringVisible,
        onCommit = {
            ringVisible = false
            onCommit(it)
        },
        onDismiss = { ringVisible = false }
    )
}

@Composable
private fun ThemeBackdropSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

