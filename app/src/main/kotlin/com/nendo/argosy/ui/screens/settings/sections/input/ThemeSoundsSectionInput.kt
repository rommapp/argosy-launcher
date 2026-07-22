package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.themeSoundsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeSoundsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeSoundsSections

internal class ThemeSoundsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = ThemeSoundsLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, themeSoundsMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, themeSoundsMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        val item = themeSoundsItemAtFocusIndex(state.focusedIndex, layoutState())
        if (item is ThemeSoundsItem.SoundTypeItem) {
            viewModel.soundManager.play(item.soundType)
        }
        return InputResult.handled(SoundType.SILENT)
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        val item = themeSoundsItemAtFocusIndex(state.focusedIndex, layoutState())
        if (item is ThemeSoundsItem.SoundTypeItem &&
            state.sounds.soundConfigs.containsKey(item.soundType)
        ) {
            viewModel.resetSoundToDefault(item.soundType)
            return InputResult.HANDLED
        }
        return InputResult.handled(SoundType.SILENT)
    }

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(themeSoundsSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(themeSoundsSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (themeSoundsItemAtFocusIndex(state.focusedIndex, layoutState())) {
            ThemeSoundsItem.UiSoundsVolume -> if (state.sounds.enabled) {
                viewModel.adjustSoundVolume(direction)
                return InputResult.HANDLED
            }
            ThemeSoundsItem.UiSoundsToggle -> {
                val target = direction > 0
                if (target == state.sounds.enabled) return InputResult.handled(SoundType.SILENT)
                viewModel.setSoundEnabled(target)
                if (target) {
                    viewModel.soundManager.setEnabled(true)
                    viewModel.soundManager.play(SoundType.TOGGLE)
                }
                return InputResult.handled(SoundType.SILENT)
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
