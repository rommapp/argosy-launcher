package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.input.BoxArtSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.BuiltinControlsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.BuiltinVideoSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.EmulatorsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.PlatformDetailSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.AmbientLedSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.CoreOptionsSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.DriversSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.InterfaceSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.LightSectionsInput
import com.nendo.argosy.ui.screens.settings.sections.input.ShaderStackSectionInput
import com.nendo.argosy.ui.screens.settings.sections.input.StorageSectionInput

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit
) : InputHandler {

    companion object {
        internal const val SLIDER_STEP = 10
        internal const val HUE_STEP = 10f

        private val TOP_LEVEL_SECTIONS = listOf(
            SettingsSection.PLATFORMS,
            SettingsSection.BUILTIN_EMULATOR,
            SettingsSection.STORAGE,
            SettingsSection.INTERFACE,
            SettingsSection.CONTROLS,
            SettingsSection.SERVER,
            SettingsSection.BIOS,
            SettingsSection.RETRO_ACHIEVEMENTS,
            SettingsSection.SOCIAL,
            SettingsSection.PERMISSIONS,
            SettingsSection.ABOUT
        )
    }

    private val modalRouter = ModalInputRouter(viewModel)
    private val lightHandler = LightSectionsInput(viewModel)

    private val handlers: Map<SettingsSection, InputHandler> = buildMap {
        put(SettingsSection.BUILTIN_VIDEO, BuiltinVideoSectionInput(viewModel))
        put(SettingsSection.PLATFORMS, EmulatorsSectionInput(viewModel))
        put(SettingsSection.PLATFORM_DETAIL, PlatformDetailSectionInput(viewModel))
        put(SettingsSection.BUILTIN_CONTROLS, BuiltinControlsSectionInput(viewModel))
        put(SettingsSection.BOX_ART, BoxArtSectionInput(viewModel))
        put(SettingsSection.INTERFACE, InterfaceSectionInput(viewModel))
        put(SettingsSection.AMBIENT_LED, AmbientLedSectionInput(viewModel))
        put(SettingsSection.SHADER_STACK, ShaderStackSectionInput(viewModel))
        put(SettingsSection.CORE_OPTIONS, CoreOptionsSectionInput(viewModel))
        put(SettingsSection.STORAGE, StorageSectionInput(viewModel))
        put(SettingsSection.DRIVERS, DriversSectionInput(viewModel))
        for (s in listOf(
            SettingsSection.BIOS, SettingsSection.SERVER, SettingsSection.HOME_SCREEN,
            SettingsSection.CONTROLS, SettingsSection.SYNC_SETTINGS,
            SettingsSection.ABOUT, SettingsSection.STEAM_SETTINGS, SettingsSection.CORE_MANAGEMENT,
            SettingsSection.SOCIAL
        )) {
            put(s, lightHandler)
        }
    }

    private fun dispatch(method: InputMethod, fallback: () -> InputResult): InputResult {
        val state = viewModel.uiState.value
        modalRouter.intercept(state, method)?.let { return it }
        val handler = handlers[state.currentSection]
        return handler?.let {
            val result = when (method) {
                InputMethod.UP -> it.onUp()
                InputMethod.DOWN -> it.onDown()
                InputMethod.LEFT -> it.onLeft()
                InputMethod.RIGHT -> it.onRight()
                InputMethod.CONFIRM -> it.onConfirm()
                InputMethod.BACK -> it.onBack()
                InputMethod.CONTEXT_MENU -> it.onContextMenu()
                InputMethod.SECONDARY_ACTION -> it.onSecondaryAction()
                InputMethod.PREV_SECTION -> it.onPrevSection()
                InputMethod.NEXT_SECTION -> it.onNextSection()
                InputMethod.PREV_TRIGGER -> it.onPrevTrigger()
                InputMethod.NEXT_TRIGGER -> it.onNextTrigger()
                InputMethod.MENU -> it.onMenu()
                InputMethod.SELECT -> it.onSelect()
                InputMethod.LEFT_STICK_CLICK -> it.onLeftStickClick()
                InputMethod.RIGHT_STICK_CLICK -> it.onRightStickClick()
                InputMethod.LONG_CONFIRM -> it.onLongConfirm()
            }
            if (result != InputResult.UNHANDLED) result else fallback()
        } ?: fallback()
    }

    override fun onUp(): InputResult = dispatch(InputMethod.UP) {
        viewModel.moveFocus(-1)
        InputResult.HANDLED
    }

    override fun onDown(): InputResult = dispatch(InputMethod.DOWN) {
        viewModel.moveFocus(1)
        InputResult.HANDLED
    }

    override fun onLeft(): InputResult = dispatch(InputMethod.LEFT) {
        InputResult.UNHANDLED
    }

    override fun onRight(): InputResult = dispatch(InputMethod.RIGHT) {
        InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult = dispatch(InputMethod.CONFIRM) {
        viewModel.handleConfirm()
    }

    override fun onLongConfirm(): InputResult = dispatch(InputMethod.LONG_CONFIRM) {
        InputResult.UNHANDLED
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        modalRouter.intercept(state, InputMethod.BACK)?.let { return it }
        return if (!viewModel.navigateBack()) {
            onBackNavigation()
            InputResult.HANDLED
        } else {
            InputResult.HANDLED
        }
    }

    override fun onContextMenu(): InputResult = dispatch(InputMethod.CONTEXT_MENU) {
        InputResult.UNHANDLED
    }

    override fun onSecondaryAction(): InputResult = dispatch(InputMethod.SECONDARY_ACTION) {
        InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult = dispatch(InputMethod.PREV_SECTION) {
        InputResult.HANDLED
    }

    override fun onNextSection(): InputResult = dispatch(InputMethod.NEXT_SECTION) {
        InputResult.HANDLED
    }

    override fun onPrevTrigger(): InputResult = dispatch(InputMethod.PREV_TRIGGER) {
        InputResult.UNHANDLED
    }

    override fun onNextTrigger(): InputResult = dispatch(InputMethod.NEXT_TRIGGER) {
        InputResult.UNHANDLED
    }

    private fun jumpTopLevelSection(direction: Int): InputResult {
        val current = viewModel.uiState.value.currentSection
        val index = TOP_LEVEL_SECTIONS.indexOf(current)
        if (index < 0) return InputResult.UNHANDLED
        val nextIndex = index + direction
        if (nextIndex !in TOP_LEVEL_SECTIONS.indices) return InputResult.UNHANDLED
        viewModel.navigateToSection(TOP_LEVEL_SECTIONS[nextIndex])
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult = InputResult.UNHANDLED

    override fun onLeftStickClick(): InputResult = InputResult.UNHANDLED

    override fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
