package com.nendo.argosy.core.emulator

sealed class EmulatorDownloadState {
    data object Idle : EmulatorDownloadState()
    data class Downloading(val progress: Float) : EmulatorDownloadState()
    data object WaitingForInstall : EmulatorDownloadState()
    data object Installed : EmulatorDownloadState()
    data class Failed(val message: String) : EmulatorDownloadState()
}
