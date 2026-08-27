package com.nendo.argosy.ui.screens.touchlayout

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.touch.LayoutDefaults
import com.nendo.argosy.libretro.touch.ResolvedLayout
import com.nendo.argosy.libretro.touch.TouchBackdropCache
import com.nendo.argosy.libretro.touch.TouchLayoutEditor
import com.nendo.argosy.libretro.touch.TouchLayoutRegistry
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.repository.TouchLayoutRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.EnumValueControl
import kotlinx.coroutines.launch

@Composable
fun TouchLayoutEditorModal(
    repository: TouchLayoutRepository,
    onDismiss: () -> Unit
) {
    val supported = remember { LibretroCoreRegistry.getSupportedPlatforms().sorted() }
    if (supported.isEmpty()) {
        onDismiss()
        return
    }
    var platformIndex by remember { mutableIntStateOf(0) }
    var orientation by remember { mutableIntStateOf(Configuration.ORIENTATION_LANDSCAPE) }
    val platformSlug = supported[platformIndex]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val spec = remember(platformSlug) { TouchLayoutRegistry.forPlatform(platformSlug) }

    var currentLayout by remember(platformSlug, orientation) {
        mutableStateOf<ResolvedLayout?>(null)
    }
    LaunchedEffect(platformSlug, orientation) {
        currentLayout = repository.load(spec, platformSlug, orientation)
    }

    val backdrop = remember(platformSlug, orientation) {
        TouchBackdropCache.load(context, platformSlug, orientation)
    }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val inputHandler = remember {
        object : InputHandler {
            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onUp(): InputResult = InputResult.HANDLED
            override fun onDown(): InputResult = InputResult.HANDLED
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (backdrop != null) {
            Image(
                bitmap = backdrop.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(top = 96.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 96.dp)
                    .background(Color(0xFF202020))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.touchlayout_editor_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnumValueControl(
                    value = platformSlug,
                    focused = false,
                    onPrev = { platformIndex = (platformIndex - 1 + supported.size) % supported.size },
                    onNext = { platformIndex = (platformIndex + 1) % supported.size },
                    onOpen = {},
                    modifier = Modifier.width(180.dp)
                )

                Spacer(modifier = Modifier.padding(8.dp))

                ActionButton(
                    label = stringResource(
                        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            R.string.touchlayout_editor_orientation_portrait
                        } else {
                            R.string.touchlayout_editor_orientation_landscape
                        }
                    ),
                    onClick = {
                        orientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                            Configuration.ORIENTATION_LANDSCAPE
                        } else {
                            Configuration.ORIENTATION_PORTRAIT
                        }
                    }
                )

                Spacer(modifier = Modifier.padding(8.dp))

                ActionButton(label = stringResource(R.string.touchlayout_editor_close), onClick = onDismiss, primary = true)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 96.dp)) {
            val layout = currentLayout
            if (layout != null) {
                val editorState = com.nendo.argosy.libretro.touch.rememberTouchEditorState(layout)
                com.nendo.argosy.libretro.touch.TouchLayoutEditor(
                    state = editorState,
                    spec = spec
                )
                com.nendo.argosy.libretro.touch.TouchEditorToolbar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    onSave = {
                        scope.launch {
                            val saved = editorState.snapshot()
                            repository.save(platformSlug, orientation, saved)
                            currentLayout = saved
                        }
                    },
                    onReset = {
                        editorState.clearOverrides()
                        scope.launch {
                            repository.reset(platformSlug, orientation)
                            currentLayout = LayoutDefaults.forOrientation(spec, orientation)
                        }
                    },
                    onCancel = onDismiss
                )
            }
        }
    }
}
