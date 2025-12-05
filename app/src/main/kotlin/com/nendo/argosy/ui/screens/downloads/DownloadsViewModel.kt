package com.nendo.argosy.ui.screens.downloads

import androidx.lifecycle.ViewModel
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.ui.input.InputHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val state: StateFlow<DownloadQueueState> = downloadManager.state

    fun cancelDownload(rommId: Long) {
        downloadManager.cancelDownload(rommId)
    }

    fun clearCompleted() {
        downloadManager.clearCompleted()
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): Boolean = false
        override fun onDown(): Boolean = false
        override fun onLeft(): Boolean = false
        override fun onRight(): Boolean = false
        override fun onConfirm(): Boolean = false
        override fun onBack(): Boolean {
            onBack()
            return true
        }
        override fun onMenu(): Boolean = false
    }
}
