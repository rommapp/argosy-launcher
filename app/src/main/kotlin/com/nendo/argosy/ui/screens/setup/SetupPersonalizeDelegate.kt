package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckController
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckState
import com.nendo.argosy.ui.theme.AccentHue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupPersonalizeState(
    val gamepadConnected: Boolean = false,
    val controllerNintendo: Boolean = false,
    val buttonCheck: ButtonCheckState = ButtonCheckState(),
    val look: SetupLookState = SetupLookState()
)

/**
 * The controller and customization pages: whether a gamepad is connected, the chosen face-button
 * layout, the button check, and the theme, Home and library choices, each written to its stored
 * preference as it changes.
 */
class SetupPersonalizeDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val controllerTracker: ConnectedControllerTracker
) {
    private val _state = MutableStateFlow(SetupPersonalizeState())
    val state: StateFlow<SetupPersonalizeState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    lateinit var buttonCheck: ButtonCheckController
        private set

    val gamepadConnected: Boolean get() = controllerTracker.controllers.value.isNotEmpty()

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        buttonCheck = ButtonCheckController(preferencesRepository, scope)
        scope.launch {
            controllerTracker.controllers.map { it.isNotEmpty() }.distinctUntilChanged().collect { connected ->
                _state.update { it.copy(gamepadConnected = connected) }
            }
        }
        scope.launch {
            buttonCheck.state.collect { check -> _state.update { it.copy(buttonCheck = check) } }
        }
        scope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                val nintendo = ControllerDetector.isNintendoLayout(prefs.controllerLayout, controllerTracker.activeLayout.value)
                _state.update {
                    it.copy(
                        controllerNintendo = nintendo,
                        look = SetupLookState(
                            themeMode = prefs.themeMode,
                            primaryColor = prefs.primaryColor,
                            homeLayout = prefs.homeLayout,
                            libraryLayout = prefs.libraryLayout,
                            boxArtShape = prefs.boxArtShape
                        )
                    )
                }
            }
        }
    }

    fun recheckControllers() = controllerTracker.refresh()

    fun selectLayout(nintendo: Boolean) {
        _state.update { it.copy(controllerNintendo = nintendo) }
        scope.launch { preferencesRepository.setControllerLayout(if (nintendo) LAYOUT_NINTENDO else LAYOUT_XBOX) }
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setAccent(color: Int?) {
        scope.launch { preferencesRepository.setPrimaryColor(color) }
    }

    fun setHomeLayout(kind: HomeLayoutKind) {
        val settings = _state.value.look.homeLayout.copy(selected = kind)
        scope.launch { preferencesRepository.setHomeLayout(settings) }
    }

    fun setLibraryLayout(layout: LibraryLayout) {
        scope.launch { preferencesRepository.setLibraryLayout(layout) }
    }

    fun setBoxArtShape(shape: BoxArtShape) {
        scope.launch { preferencesRepository.setBoxArtShape(shape) }
    }

    /**
     * Steps the segmented control behind [target] by [direction]. False at either end, where the
     * value stays.
     */
    fun step(target: SetupTarget, direction: Int): Boolean {
        val look = _state.value.look
        return when (target) {
            SetupTarget.ThemeMode -> stepIn(ThemeMode.entries, look.themeMode, direction, ::setThemeMode)
            SetupTarget.Accent -> stepIn(AccentHue.SWATCHES, look.primaryColor, direction, ::setAccent)
            SetupTarget.HomeLayout -> stepIn(HomeLayoutKind.entries, look.homeLayout.selected, direction, ::setHomeLayout)
            SetupTarget.LibraryLayout -> stepIn(LibraryLayout.entries, look.libraryLayout, direction, ::setLibraryLayout)
            SetupTarget.BoxArtShape -> stepIn(SETUP_BOX_ART_SHAPES, look.boxArtShape, direction, ::setBoxArtShape)
            else -> false
        }
    }

    private fun <T> stepIn(options: List<T>, current: T, direction: Int, apply: (T) -> Unit): Boolean {
        val next = options.getOrNull(options.indexOf(current).coerceAtLeast(0) + direction) ?: return false
        apply(next)
        return true
    }

    private companion object {
        const val LAYOUT_NINTENDO = "nintendo"
        const val LAYOUT_XBOX = "xbox"
    }
}
