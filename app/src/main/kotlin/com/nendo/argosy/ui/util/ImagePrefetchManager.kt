package com.nendo.argosy.ui.util

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImagePrefetchManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imageLoader = context.imageLoader

    fun prefetchBackgrounds(paths: List<String>) {
        paths.forEach { path ->
            try {
                val data = if (path.startsWith("/")) File(path) else path
                val request = ImageRequest.Builder(context)
                    .data(data)
                    .size(640, 360)
                    .build()
                imageLoader.enqueue(request)
            } catch (_: Exception) { }
        }
    }
}
