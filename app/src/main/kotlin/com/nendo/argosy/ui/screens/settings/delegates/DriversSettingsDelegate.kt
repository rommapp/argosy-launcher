package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.emulator.DriverFetcherRepository
import com.nendo.argosy.ui.screens.settings.DriverArtifactUi
import com.nendo.argosy.ui.screens.settings.DriverDownloadState
import com.nendo.argosy.ui.screens.settings.DriverGroupUi
import com.nendo.argosy.ui.screens.settings.DriverReleaseUi
import com.nendo.argosy.ui.screens.settings.DriversState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class DriversSettingsDelegate @Inject constructor(
    private val driverFetcher: DriverFetcherRepository
) {
    private val _state = MutableStateFlow(DriversState())
    val state: StateFlow<DriversState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    fun loadDrivers(scope: CoroutineScope, force: Boolean = false) {
        if (!force && _state.value.groups.isNotEmpty()) {
            refreshDownloadedFiles()
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            val gpu = driverFetcher.getGpuInfo()
            _state.update {
                it.copy(
                    isLoading = true,
                    gpuModel = gpu.rawModel,
                    recommendedDriver = gpu.recommendedDriver
                )
            }
            val groups = driverFetcher.fetchAllGroups().map { it.toUi() }
            _state.update {
                it.copy(
                    groups = groups,
                    isLoading = false,
                    downloadedFiles = driverFetcher.listDownloadedFiles().map { f -> f.name }
                )
            }
        }
    }

    fun refreshDownloadedFiles() {
        _state.update {
            it.copy(downloadedFiles = driverFetcher.listDownloadedFiles().map { f -> f.name })
        }
    }

    fun toggleGroupExpanded(index: Int) {
        _state.update {
            val newIndex = if (it.expandedGroupIndex == index) -1 else index
            it.copy(expandedGroupIndex = newIndex, releaseFocusIndex = 0)
        }
    }

    fun moveReleaseFocus(delta: Int) {
        val state = _state.value
        val group = state.groups.getOrNull(state.expandedGroupIndex) ?: return
        if (group.releases.isEmpty()) return
        val next = (state.releaseFocusIndex + delta).coerceIn(0, group.releases.size - 1)
        _state.update { it.copy(releaseFocusIndex = next) }
    }

    fun moveGroupActionFocus(delta: Int) {
        _state.update { it.copy(groupActionIndex = (it.groupActionIndex + delta).mod(2)) }
    }

    fun downloadFocusedArtifact(scope: CoroutineScope) {
        val state = _state.value
        val group = state.groups.getOrNull(state.expandedGroupIndex) ?: return
        val release = group.releases.getOrNull(state.releaseFocusIndex) ?: return
        val artifact = release.artifacts.firstOrNull() ?: return
        downloadArtifact(scope, artifact)
    }

    fun downloadArtifact(scope: CoroutineScope, artifact: DriverArtifactUi) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.update {
                it.copy(
                    activeDownload = DriverDownloadState(
                        artifactName = artifact.name,
                        downloaded = 0L,
                        total = artifact.size
                    )
                )
            }
            val asset = com.nendo.argosy.data.remote.github.GitHubAsset(
                name = artifact.name,
                downloadUrl = artifact.downloadUrl,
                size = artifact.size
            )
            val result = driverFetcher.downloadAsset(asset) { downloaded, total ->
                _state.update { current ->
                    val active = current.activeDownload ?: return@update current
                    if (active.artifactName != artifact.name) return@update current
                    current.copy(
                        activeDownload = active.copy(downloaded = downloaded, total = total)
                    )
                }
            }
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            activeDownload = it.activeDownload?.copy(isComplete = true),
                            downloadedFiles = driverFetcher.listDownloadedFiles().map { f -> f.name }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            activeDownload = it.activeDownload?.copy(error = error.message ?: "Download failed")
                        )
                    }
                }
        }
    }

    fun dismissActiveDownload() {
        _state.update { it.copy(activeDownload = null) }
    }

    private fun DriverFetcherRepository.DriverGroup.toUi(): DriverGroupUi {
        val firstStable = releases.firstOrNull { !it.prerelease }
        val mapped = releases.map { release ->
            val title = if (repo.useTagName) release.tagName else release.name.ifBlank { release.tagName }
            DriverReleaseUi(
                title = title,
                tagName = release.tagName,
                body = release.body.orEmpty(),
                prerelease = release.prerelease,
                isLatestStable = firstStable === release,
                artifacts = release.assets.map { asset ->
                    DriverArtifactUi(
                        name = asset.name,
                        downloadUrl = asset.downloadUrl,
                        size = asset.size
                    )
                }
            )
        }
        return DriverGroupUi(
            name = repo.name,
            repoPath = repo.path,
            sort = repo.sort,
            useTagName = repo.useTagName,
            releases = mapped,
            error = error
        )
    }
}
