package com.nendo.argosy.libretro.touch

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.repository.TouchLayoutRepository
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.launch

@Composable
fun OnScreenControlsHost(
    retroView: GLRetroView,
    platformSlug: String,
    orientation: Int,
    isGamepadConnected: Boolean,
    settings: BuiltinEmulatorSettings,
    rotationKey: Int,
    baselineRotation: Int = 0,
    editMode: Boolean = false,
    repository: TouchLayoutRepository? = null,
    onExitEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!editMode) {
        if (!settings.showTouchControlsWhenNoGamepad) return
        if (isGamepadConnected) return
    }

    val spec = remember(platformSlug, settings.touchControlsGenesis6Button, settings.touchControlsColouredFaceButtons) {
        TouchLayoutRegistry.forPlatform(
            platformSlug,
            genesis6Button = settings.touchControlsGenesis6Button,
            colouredPsx = settings.touchControlsColouredFaceButtons
        )
    }

    val overrideState by produceState<ResolvedLayout?>(
        initialValue = null,
        platformSlug, orientation, spec, repository
    ) {
        value = repository?.load(spec, platformSlug, orientation)
    }

    val baseResolved = overrideState ?: LayoutDefaults.forOrientation(spec, orientation)
    val resolved = remember(
        baseResolved,
        settings.touchControlsSwapHanded,
        settings.touchControlsSizeScale,
        settings.touchControlsMirror180,
        rotationKey,
        baselineRotation
    ) {
        baseResolved
            .applyHandedness(settings.touchControlsSwapHanded)
            .applySizeScale(settings.touchControlsSizeScale)
            .applyMirror180(settings.touchControlsMirror180, rotationKey, baselineRotation)
    }
    val opacity = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
        settings.touchControlsOpacityPortrait
    } else {
        settings.touchControlsOpacityLandscape
    }

    if (editMode && repository != null && onExitEdit != null) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        TouchLayoutEditor(
            spec = spec,
            initial = baseResolved,
            onSave = { layout ->
                scope.launch {
                    repository.save(platformSlug, orientation, layout)
                    onExitEdit()
                }
            },
            onCancel = onExitEdit,
            onReset = {
                scope.launch {
                    repository.reset(platformSlug, orientation)
                    onExitEdit()
                }
            },
            modifier = modifier
        )
    } else {
        OnScreenControls(
            retroView = retroView,
            spec = spec,
            resolved = resolved,
            opacity = opacity,
            sizeScale = settings.touchControlsSizeScale,
            haptic = settings.touchControlsHaptic,
            modifier = modifier
        )
    }
}
