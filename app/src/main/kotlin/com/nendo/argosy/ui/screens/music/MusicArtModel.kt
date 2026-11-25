package com.nendo.argosy.ui.screens.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.nendo.argosy.domain.model.MusicArt
import java.io.File

/**
 * The Coil request for [art], or null when there is none. Remote art is cached under its
 * credential-free cache key, never under the url.
 */
@Composable
fun rememberMusicArtModel(art: MusicArt?): ImageRequest? {
    val context = LocalContext.current
    return remember(art, context) {
        when (art) {
            null -> null
            is MusicArt.Remote -> ImageRequest.Builder(context)
                .data(art.url)
                .memoryCacheKey(art.cacheKey)
                .diskCacheKey(art.cacheKey)
                .build()
            is MusicArt.Local -> ImageRequest.Builder(context)
                .data(File(art.path))
                .build()
        }
    }
}
