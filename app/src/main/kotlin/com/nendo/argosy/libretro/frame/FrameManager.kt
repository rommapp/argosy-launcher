package com.nendo.argosy.libretro.frame

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrameManager(
    private val frameRegistry: FrameRegistry,
    private val frameDownloader: FrameDownloader,
    private val platformSlug: String,
    private val scope: CoroutineScope,
    initialFrameId: String?,
    private val onFrameChanged: ((String?) -> Unit)? = null
) {
    companion object {
        private const val TAG = "FrameManager"
    }

    var selectedFrameId by mutableStateOf(initialFrameId)
        private set

    init {
        onFrameChanged?.invoke(initialFrameId)
    }

    var previewBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var downloadingFrameId by mutableStateOf<String?>(null)
        private set

    private var previewJob: Job? = null

    val availableFrames: List<FrameRegistry.FrameEntry>
        get() = frameRegistry.getFramesForPlatform(platformSlug)

    val localFrames: List<FrameRegistry.FrameEntry>
        get() = frameRegistry.getInstalledFramesForPlatform(platformSlug)

    fun selectFrame(frameId: String?) {
        if (frameId == selectedFrameId) return
        selectedFrameId = frameId
        onFrameChanged?.invoke(frameId)
        renderPreview()
    }

    fun nextFrame(localOnly: Boolean): String? {
        val frames = if (localOnly) localFrames else availableFrames
        if (frames.isEmpty()) return selectedFrameId

        val currentIndex = frames.indexOfFirst { it.id == selectedFrameId }
        val nextIndex = when {
            currentIndex < 0 -> 0
            currentIndex >= frames.size - 1 -> -1
            else -> currentIndex + 1
        }

        val newFrameId = if (nextIndex < 0) null else frames[nextIndex].id
        selectFrame(newFrameId)
        return newFrameId
    }

    fun previousFrame(localOnly: Boolean): String? {
        val frames = if (localOnly) localFrames else availableFrames
        if (frames.isEmpty()) return selectedFrameId

        val currentIndex = frames.indexOfFirst { it.id == selectedFrameId }
        val prevIndex = when {
            currentIndex < 0 -> frames.size - 1
            currentIndex == 0 -> -1
            else -> currentIndex - 1
        }

        val newFrameId = if (prevIndex < 0) null else frames[prevIndex].id
        selectFrame(newFrameId)
        return newFrameId
    }

    fun cycleFrame(direction: Int, localOnly: Boolean): String? {
        return if (direction > 0) nextFrame(localOnly) else previousFrame(localOnly)
    }

    fun downloadFrame(entry: FrameRegistry.FrameEntry, onComplete: (Boolean) -> Unit = {}) {
        if (isDownloading) return
        isDownloading = true
        downloadingFrameId = entry.id

        scope.launch {
            val result = frameDownloader.downloadFrame(entry)
            withContext(Dispatchers.Main) {
                isDownloading = false
                downloadingFrameId = null
                if (result.isSuccess) {
                    frameRegistry.invalidateInstalledCache()
                }
                onComplete(result.isSuccess)
            }
        }
    }

    fun downloadAndSelectFrame(entry: FrameRegistry.FrameEntry) {
        downloadFrame(entry) { success ->
            if (success) {
                selectFrame(entry.id)
            }
        }
    }

    fun loadCurrentFrameBitmap(): Bitmap? {
        val frameId = selectedFrameId ?: return null
        return frameRegistry.loadFrame(frameId)
    }

    fun renderPreview() {
        previewJob?.cancel()
        previewJob = scope.launch(Dispatchers.IO) {
            val frameId = selectedFrameId
            val bitmap = if (frameId != null) {
                frameRegistry.loadFrame(frameId)
            } else {
                null
            }
            val imageBitmap = bitmap?.asImageBitmap()
            withContext(Dispatchers.Main) {
                previewBitmap = imageBitmap
            }
        }
    }

    fun isFrameInstalled(frameId: String): Boolean =
        frameRegistry.isInstalled(frameId)

    fun getFrameEntry(frameId: String): FrameRegistry.FrameEntry? =
        frameRegistry.findById(frameId)

    fun destroy() {
        previewJob?.cancel()
    }
}
