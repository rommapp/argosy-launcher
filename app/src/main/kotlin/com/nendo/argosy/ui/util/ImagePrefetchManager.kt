package com.nendo.argosy.ui.util

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.nendo.argosy.ui.common.fileImageModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
                val request = ImageRequest.Builder(context)
                    .data(fileImageModel(path))
                    .size(640, 360)
                    .build()
                imageLoader.enqueue(request)
            } catch (_: Exception) { }
        }
    }
}
