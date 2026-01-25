package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class BuiltinVideoItem(
    val key: String,
    val section: String,
    val visibleWhen: (BuiltinVideoState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : BuiltinVideoItem(key, section)

    data object Shader : BuiltinVideoItem("shader", "shaders")
    data object Filter : BuiltinVideoItem("filter", "shaders")
    data object AspectRatio : BuiltinVideoItem("aspectRatio", "display")
    data object Rotation : BuiltinVideoItem("rotation", "display")
    data object OverscanCrop : BuiltinVideoItem("overscanCrop", "display")
    data object BlackFrameInsertion : BuiltinVideoItem(
        "blackFrameInsertion",
        "display",
        visibleWhen = { it.canEnableBlackFrameInsertion }
    )
    data object FastForwardSpeed : BuiltinVideoItem("fastForwardSpeed", "performance")
    data object SkipDuplicateFrames : BuiltinVideoItem("skipDuplicateFrames", "performance")
    data object LowLatencyAudio : BuiltinVideoItem("lowLatencyAudio", "performance")

    companion object {
        private val ShadersHeader = Header("shadersHeader", "shaders", "Shaders")
        private val DisplayHeader = Header("displayHeader", "display", "Display")
        private val PerformanceHeader = Header("performanceHeader", "performance", "Performance")

        val ALL: List<BuiltinVideoItem> = listOf(
            ShadersHeader,
            Shader,
            Filter,
            DisplayHeader,
            AspectRatio,
            Rotation,
            OverscanCrop,
            BlackFrameInsertion,
            PerformanceHeader,
            FastForwardSpeed,
            SkipDuplicateFrames,
            LowLatencyAudio
        )
    }
}

private val builtinVideoLayout = SettingsLayout<BuiltinVideoItem, BuiltinVideoState>(
    allItems = BuiltinVideoItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun builtinVideoMaxFocusIndex(state: BuiltinVideoState): Int =
    builtinVideoLayout.maxFocusIndex(state)

internal fun builtinVideoItemAtFocusIndex(index: Int, state: BuiltinVideoState): BuiltinVideoItem? =
    builtinVideoLayout.itemAtFocusIndex(index, state)

internal fun builtinVideoSections(state: BuiltinVideoState) =
    builtinVideoLayout.buildSections(state)

@Composable
fun BuiltinVideoSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val videoState = uiState.builtinVideo

    val visibleItems = remember(videoState) {
        builtinVideoLayout.visibleItems(videoState)
    }
    val sections = remember(videoState) {
        builtinVideoLayout.buildSections(videoState)
    }

    fun isFocused(item: BuiltinVideoItem): Boolean =
        uiState.focusedIndex == builtinVideoLayout.focusIndexOf(item, videoState)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { builtinVideoLayout.focusToListIndex(it, videoState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is BuiltinVideoItem.Header -> {
                    if (item.section != "shaders") {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Dimens.spacingSm,
                            top = Dimens.spacingXs,
                            bottom = Dimens.spacingXs
                        )
                    )
                }

                BuiltinVideoItem.Shader -> CyclePreference(
                    title = "Shader",
                    value = videoState.shader,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinShader(1) }
                )

                BuiltinVideoItem.Filter -> CyclePreference(
                    title = "Filter",
                    value = videoState.filter,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinFilter(1) }
                )

                BuiltinVideoItem.AspectRatio -> CyclePreference(
                    title = "Aspect Ratio",
                    value = videoState.aspectRatio,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinAspectRatio(1) }
                )

                BuiltinVideoItem.Rotation -> CyclePreference(
                    title = "Screen Rotation",
                    value = videoState.rotation,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinRotation(1) }
                )

                BuiltinVideoItem.OverscanCrop -> CyclePreference(
                    title = "Crop Overscan",
                    value = videoState.overscanCrop,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinOverscanCrop(1) }
                )

                BuiltinVideoItem.BlackFrameInsertion -> SwitchPreference(
                    title = "Black Frame Insertion",
                    subtitle = "Reduce motion blur (requires 120Hz+ display)",
                    isEnabled = videoState.blackFrameInsertion,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinBlackFrameInsertion(it) }
                )

                BuiltinVideoItem.FastForwardSpeed -> CyclePreference(
                    title = "Fast Forward Speed",
                    value = videoState.fastForwardSpeed,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBuiltinFastForwardSpeed(1) }
                )

                BuiltinVideoItem.SkipDuplicateFrames -> SwitchPreference(
                    title = "Skip Duplicate Frames",
                    subtitle = "Reduce CPU usage by skipping unchanged frames",
                    isEnabled = videoState.skipDuplicateFrames,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinSkipDuplicateFrames(it) }
                )

                BuiltinVideoItem.LowLatencyAudio -> SwitchPreference(
                    title = "Low Latency Audio",
                    subtitle = "Reduce audio delay for better responsiveness",
                    isEnabled = videoState.lowLatencyAudio,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBuiltinLowLatencyAudio(it) }
                )
            }
        }
    }
}
