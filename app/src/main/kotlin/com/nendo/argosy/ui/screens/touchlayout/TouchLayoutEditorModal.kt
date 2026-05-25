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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.touch.LayoutDefaults
import com.nendo.argosy.libretro.touch.ResolvedLayout
import com.nendo.argosy.libretro.touch.TouchBackdropCache
import com.nendo.argosy.libretro.touch.TouchLayoutEditor
import com.nendo.argosy.libretro.touch.TouchLayoutRegistry
import com.nendo.argosy.data.repository.TouchLayoutRepository
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
                text = "Customize touch controls",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    platformIndex = (platformIndex - 1 + supported.size) % supported.size
                }) { Text("◀") }
                Text(
                    text = platformSlug,
                    color = Color.White,
                    modifier = Modifier.width(140.dp)
                )
                OutlinedButton(onClick = {
                    platformIndex = (platformIndex + 1) % supported.size
                }) { Text("▶") }

                Spacer(modifier = Modifier.padding(8.dp))

                OutlinedButton(onClick = {
                    orientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        Configuration.ORIENTATION_LANDSCAPE
                    } else {
                        Configuration.ORIENTATION_PORTRAIT
                    }
                }) {
                    Text(if (orientation == Configuration.ORIENTATION_PORTRAIT) "Portrait" else "Landscape")
                }

                Spacer(modifier = Modifier.padding(8.dp))

                Button(onClick = onDismiss) { Text("Close") }
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
