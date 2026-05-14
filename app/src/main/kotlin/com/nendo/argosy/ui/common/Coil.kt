package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.nendo.argosy.data.cache.ImageCacheManager
import java.io.File

val LocalImageCacheManager = staticCompositionLocalOf<ImageCacheManager?> { null }

/**
 * Resolves a path string into a Coil-compatible image model. Absolute filesystem
 * paths (those starting with "/") become a [File] so Coil treats them as local
 * disk reads; everything else is passed through verbatim, leaving Coil to
 * interpret it as a URL or other supported model.
 */
@Composable
fun rememberFileImageModel(path: String?): Any? = remember(path) {
    when {
        path == null -> null
        path.startsWith("/") -> File(path)
        else -> path
    }
}

/**
 * Non-composable variant for call sites that build models off the composition,
 * e.g. inside lambdas passed to image prefetch helpers.
 */
fun fileImageModel(path: String?): Any? = when {
    path == null -> null
    path.startsWith("/") -> File(path)
    else -> path
}

@Composable
fun rememberResolvedCoverPath(gameId: Long, source: String?): String? {
    val manager = LocalImageCacheManager.current
    var resolved by remember(gameId, source) { mutableStateOf(source) }

    LaunchedEffect(gameId, source) {
        if (manager == null || source.isNullOrBlank()) return@LaunchedEffect
        if (source.startsWith("/")) return@LaunchedEffect
        manager.queueCoverCacheByGameId(source, gameId)
        manager.localCoverWritten.collect { (id, localPath) ->
            if (id == gameId) {
                resolved = localPath
            }
        }
    }

    return resolved
}
