package com.nendo.argosy.data.steam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor no-op stub. The `full` flavor implements Steam library discovery and depot
 * downloads via the JavaSteam library. The FOSS build ships without Steam, so downloads
 * never start and queues stay empty; this exposes the same public surface consumed by
 * shared code (download/queue state flows and control methods) as no-ops.
 */
@Singleton
class SteamContentManager @Inject constructor() {
    val downloadState: StateFlow<SteamDownloadState> = MutableStateFlow(SteamDownloadState.Idle)
    val activeDownload: StateFlow<SteamDownloadProgress?> = MutableStateFlow(null)
    val downloadQueue: StateFlow<List<QueuedSteamDownload>> = MutableStateFlow(emptyList())
    val completedDownloads: StateFlow<List<SteamDownloadProgress>> = MutableStateFlow(emptyList())

    suspend fun hasPendingDownloads(): Boolean = false

    suspend fun discoverLocalSteamGames(): Int = 0

    fun queueDownloadOptimistic(appId: Long, gameName: String, coverPath: String?) {}

    fun pauseDownload() {}

    fun cancelDownload() {}

    fun cancelQueuedDownload(appId: Long) {}

    fun clearCompletedDownloads() {}

    fun hasActiveSteamDownload(): Boolean = false

    fun onDownloadSlotFreed() {}

    fun cleanup() {}
}
