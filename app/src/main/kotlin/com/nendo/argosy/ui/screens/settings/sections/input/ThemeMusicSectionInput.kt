package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.ThemeMusicItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeMusicLayoutState
import com.nendo.argosy.ui.screens.settings.sections.themeMusicItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeMusicMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.themeMusicSections

internal class ThemeMusicSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = ThemeMusicLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, themeMusicMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, themeMusicMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        if (viewModel.jumpToPrevSection(themeMusicSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        if (viewModel.jumpToNextSection(themeMusicSections(layoutState()))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (themeMusicItemAtFocusIndex(state.focusedIndex, layoutState())) {
            ThemeMusicItem.BgmVolume -> if (state.ambientAudio.enabled) {
                viewModel.adjustAmbientAudioVolume(direction)
                return InputResult.HANDLED
            }
            ThemeMusicItem.BgmToggle -> {
                val target = direction > 0
                if (target == state.ambientAudio.enabled) return InputResult.handled(SoundType.SILENT)
                viewModel.setAmbientAudioEnabled(target)
                return InputResult.handled(if (target) SoundType.TOGGLE else SoundType.SILENT)
            }
            ThemeMusicItem.BgmShuffle ->
                return toggleLeftRight(direction, state.ambientAudio.shuffle) { viewModel.setAmbientAudioShuffle(it) }
            ThemeMusicItem.GameThemeToggle ->
                return toggleLeftRight(direction, state.ambientAudio.gameDetailThemeEnabled) { viewModel.setGameDetailThemeEnabled(it) }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
