package com.nendo.argosy.libretro.touch

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onKey: (Int, Int) -> Unit = { _, _ -> },
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

    var reloadKey by remember { mutableStateOf(0) }
    val overrideState by produceState<ResolvedLayout?>(
        initialValue = null,
        platformSlug, orientation, spec, repository, reloadKey
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

    val bottomHalfOnly = orientation == Configuration.ORIENTATION_PORTRAIT &&
        !isGamepadConnected

    val areaModifier: @Composable (Modifier) -> Modifier = { base ->
        if (bottomHalfOnly) {
            base.fillMaxWidth().fillMaxHeight(0.5f)
        } else {
            base.fillMaxSize()
        }
    }
    val alignment = if (bottomHalfOnly) Alignment.BottomCenter else Alignment.Center

    Box(modifier = modifier.fillMaxSize()) {
        val inner = areaModifier(Modifier.align(alignment))
        if (editMode && repository != null && onExitEdit != null) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val editorState = rememberTouchEditorState(baseResolved)
            TouchLayoutEditor(
                state = editorState,
                spec = spec,
                sizeScale = settings.touchControlsSizeScale,
                modifier = inner
            )
            val toolbarContainer: @Composable (@Composable () -> Unit) -> Unit = { content ->
                if (bottomHalfOnly) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.5f)
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center
                    ) { content() }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) { content() }
                }
            }
            toolbarContainer {
            TouchEditorToolbar(
                onSave = {
                    scope.launch {
                        repository.save(platformSlug, orientation, editorState.snapshot())
                        reloadKey++
                        onExitEdit()
                    }
                },
                onReset = {
                    editorState.clearOverrides()
                    scope.launch {
                        repository.reset(platformSlug, orientation)
                        reloadKey++
                        onExitEdit()
                    }
                },
                onCancel = onExitEdit
            )
            }
        } else {
            OnScreenControls(
                retroView = retroView,
                spec = spec,
                resolved = resolved,
                opacity = opacity,
                sizeScale = settings.touchControlsSizeScale,
                haptic = settings.touchControlsHaptic,
                onKey = onKey,
                modifier = inner
            )
        }
    }
}
