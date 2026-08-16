package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nendo.argosy.core.media.VideoFileTypes
import com.nendo.argosy.ui.common.rememberFileImageModel
import java.util.Locale

/**
 * What a curated page draws behind its tiles. A still or an animation is drawn as an image; a video
 * plays through the same inline player the media tiles use, muted and looping, because a backdrop is
 * scenery rather than something being watched.
 */
@Composable
fun PageBackdrop(path: String?, modifier: Modifier = Modifier) {
    if (path.isNullOrBlank()) return
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension in VideoFileTypes.EXTENSIONS) {
        InlineTilePlayer(
            filePath = path,
            isPlaying = true,
            isEngaged = false,
            modifier = modifier.fillMaxSize()
        )
        return
    }
    AsyncImage(
        model = rememberFileImageModel(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
