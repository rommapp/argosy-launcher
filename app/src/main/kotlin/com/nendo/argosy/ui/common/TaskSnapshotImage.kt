package com.nendo.argosy.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.CachePolicy
import coil.request.ImageRequest
import java.io.File

/**
 * The image request for a task snapshot at [path], keyed in memory by [takenAtMillis] so a newer
 * frame copied over the same file replaces the cached one. Null without a [path].
 */
@Composable
fun rememberTaskSnapshotRequest(path: String?, takenAtMillis: Long): ImageRequest? {
    val context = LocalContext.current
    return remember(path, takenAtMillis) {
        path?.let { file ->
            ImageRequest.Builder(context)
                .data(File(file))
                .memoryCacheKey("$file@$takenAtMillis")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        }
    }
}
