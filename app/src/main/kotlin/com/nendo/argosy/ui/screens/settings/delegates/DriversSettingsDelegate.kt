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

    fun loadGpuInfo() {
        if (_state.value.gpuModel != null) return
        val gpu = driverFetcher.getGpuInfo()
        _state.update {
            it.copy(gpuModel = gpu.rawModel, recommendedDriver = gpu.recommendedDriver)
        }
    }

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

    fun openPicker(index: Int) {
        _state.update {
            if (index !in it.groups.indices) it
            else it.copy(pickerGroupIndex = index, pickerReleaseFocusIndex = 0)
        }
    }

    fun dismissPicker() {
        _state.update { it.copy(pickerGroupIndex = null, pickerReleaseFocusIndex = 0) }
    }

    fun movePickerReleaseFocus(delta: Int) {
        val state = _state.value
        val group = state.groups.getOrNull(state.pickerGroupIndex ?: return) ?: return
        if (group.releases.isEmpty()) return
        val next = (state.pickerReleaseFocusIndex + delta).coerceIn(0, group.releases.size - 1)
        _state.update { it.copy(pickerReleaseFocusIndex = next) }
    }

    fun downloadFocusedPickerRelease(scope: CoroutineScope) {
        val state = _state.value
        val group = state.groups.getOrNull(state.pickerGroupIndex ?: return) ?: return
        val release = group.releases.getOrNull(state.pickerReleaseFocusIndex) ?: return
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
            val rawTitle = if (repo.useTagName) release.tagName else release.name.ifBlank { release.tagName }
            val title = cleanTitle(rawTitle, repo)
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

    private fun cleanTitle(raw: String, repo: DriverFetcherRepository.DriverRepo): String {
        var t = raw
        for (prefix in repo.titlePrefixesToStrip) {
            if (t.startsWith(prefix, ignoreCase = true)) {
                t = t.substring(prefix.length)
                break
            }
        }
        for (suffix in repo.titleSuffixesToStrip) {
            if (t.endsWith(suffix, ignoreCase = true)) {
                t = t.substring(0, t.length - suffix.length)
                break
            }
        }
        return t.ifBlank { raw }
    }
}
