package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.PlayTimeListKind
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.PlayTimeListLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createPlayTimeListLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.playTimeListKindOf
import com.nendo.argosy.ui.screens.settings.sections.playTimeListMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.playTimeListSections

internal class PlayTimeListSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun kind(): PlayTimeListKind? =
        playTimeListKindOf(viewModel.uiState.value.currentSection)

    private fun layoutInfo(kind: PlayTimeListKind): PlayTimeListLayoutInfo =
        createPlayTimeListLayoutInfo(viewModel.uiState.value, kind)

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val kind = kind() ?: return InputResult.UNHANDLED
        return if (viewModel.moveFocusWrapped(delta, playTimeListMaxFocusIndex(layoutInfo(kind)))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onConfirm(): InputResult = InputResult.handled(SoundType.SILENT)

    override fun onPrevSection(): InputResult {
        val kind = kind() ?: return InputResult.UNHANDLED
        if (viewModel.jumpToPrevSection(playTimeListSections(layoutInfo(kind)))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val kind = kind() ?: return InputResult.UNHANDLED
        if (viewModel.jumpToNextSection(playTimeListSections(layoutInfo(kind)))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onContextMenu(): InputResult {
        if (kind() != PlayTimeListKind.GAMES) return InputResult.UNHANDLED
        viewModel.togglePlayTimeGamesSortMode()
        return InputResult.HANDLED
    }
}
