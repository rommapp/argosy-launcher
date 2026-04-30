package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

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
