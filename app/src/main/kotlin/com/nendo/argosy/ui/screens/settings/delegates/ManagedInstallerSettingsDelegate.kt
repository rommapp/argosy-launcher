package com.nendo.argosy.ui.screens.settings.delegates

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.installer.GitHubRepoUrl
import com.nendo.argosy.data.installer.InstallerFailure
import com.nendo.argosy.data.installer.InstallerJobState
import com.nendo.argosy.data.installer.ManagedInstallerManager
import com.nendo.argosy.data.installer.ManagedInstallerRepository
import com.nendo.argosy.data.installer.SeededInstallers
import com.nendo.argosy.data.local.entity.ManagedInstallerEntity
import com.nendo.argosy.data.remote.github.GitHubAsset
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.screens.settings.ManagedInstallerRow
import com.nendo.argosy.ui.screens.settings.ManagedInstallersState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ManagedInstallerSettingsDelegate @Inject constructor(
    private val repository: ManagedInstallerRepository,
    private val manager: ManagedInstallerManager
) {
    private val _state = MutableStateFlow(ManagedInstallersState())
    val state: StateFlow<ManagedInstallersState> = _state.asStateFlow()

    private var pendingVariantAssets: List<GitHubAsset> = emptyList()
    private var pendingVariantRowId: Long? = null

    fun observeJobs(scope: CoroutineScope) {
        scope.launch {
            manager.job.collect { job ->
                if (job == null) {
                    _state.update { it.copy(busyId = null, busyProgress = 0f, statusRes = null) }
                    load(scope)
                    return@collect
                }
                applyJobState(job.installerId, job.state)
            }
        }
    }

    fun load(scope: CoroutineScope) {
        scope.launch {
            val rows = withContext(Dispatchers.IO) { repository.getAll() }
            _state.update { it.copy(rows = rows.map(::toRow)) }
        }
    }

    fun openScreen(scope: CoroutineScope) {
        scope.launch {
            withContext(Dispatchers.IO) { repository.ensureSeeded() }
            val seeded = withContext(Dispatchers.IO) { repository.getAll() }
            _state.update {
                it.copy(
                    rows = seeded.map(::toRow),
                    checking = true,
                    failedId = null,
                    failedRes = null
                )
            }
            withContext(Dispatchers.IO) { manager.refresh(seeded) }
            val refreshed = withContext(Dispatchers.IO) { repository.getAll() }
            _state.update { it.copy(rows = refreshed.map(::toRow), checking = false) }
        }
    }

    fun install(scope: CoroutineScope, rowId: Long) {
        if (!manager.canInstallPackages()) {
            manager.openInstallPermissionSettings()
            return
        }
        scope.launch {
            val entity = withContext(Dispatchers.IO) { repository.getById(rowId) } ?: return@launch
            manager.install(entity)
        }
    }

    fun openAddModal() {
        _state.update {
            it.copy(
                showAddModal = true,
                addText = "",
                addFocus = TextEntryFocus(),
                addErrorRes = null
            )
        }
    }

    fun dismissAddModal() {
        _state.update {
            it.copy(
                showAddModal = false,
                addText = "",
                addFocus = TextEntryFocus(),
                addErrorRes = null
            )
        }
    }

    fun updateAddText(text: String) {
        _state.update { it.copy(addText = text, addErrorRes = null) }
    }

    fun moveAddRow(row: TextEntryRow) {
        _state.update { it.copy(addFocus = it.addFocus.copy(row = row)) }
    }

    fun moveAddButton(delta: Int) {
        _state.update { state ->
            if (state.addFocus.row != TextEntryRow.BUTTONS) return@update state
            val next = (state.addFocus.buttonIndex + delta)
                .coerceIn(TEXT_ENTRY_CANCEL_BUTTON, TEXT_ENTRY_CONFIRM_BUTTON)
            state.copy(addFocus = state.addFocus.copy(buttonIndex = next))
        }
    }

    fun confirmAdd(scope: CoroutineScope) {
        val focus = _state.value.addFocus
        when {
            focus.row == TextEntryRow.FIELD -> _state.update { it.copy(addFocus = it.addFocus.requestKeyboard()) }
            focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> dismissAddModal()
            else -> submitAdd(scope)
        }
    }

    fun submitAdd(scope: CoroutineScope) {
        val ref = GitHubRepoUrl.parse(_state.value.addText)
        if (ref == null) {
            _state.update { it.copy(addErrorRes = R.string.settings_installers_add_error_invalid) }
            return
        }
        scope.launch {
            val existing = withContext(Dispatchers.IO) { repository.getAll() }
            if (existing.any { it.repoOwner == ref.owner && it.repoName == ref.name }) {
                _state.update { it.copy(addErrorRes = R.string.settings_installers_add_error_duplicate) }
                return@launch
            }
            withContext(Dispatchers.IO) { repository.add(ref, ref.name) }
            _state.update {
                it.copy(
                    showAddModal = false,
                    addText = "",
                    addFocus = TextEntryFocus(),
                    addErrorRes = null
                )
            }
            openScreen(scope)
        }
    }

    fun requestRemove(rowId: Long) {
        _state.update { state ->
            val row = state.rows.firstOrNull { it.id == rowId } ?: return@update state
            if (row.locked) return@update state
            state.copy(confirmRemoveId = rowId, confirmRemoveName = row.displayName)
        }
    }

    fun dismissRemove() {
        _state.update { it.copy(confirmRemoveId = null, confirmRemoveName = "") }
    }

    fun confirmRemove(scope: CoroutineScope) {
        val id = _state.value.confirmRemoveId ?: return
        scope.launch {
            withContext(Dispatchers.IO) { repository.remove(id) }
            _state.update { state ->
                val staleFailure = state.failedId == id
                state.copy(
                    confirmRemoveId = null,
                    confirmRemoveName = "",
                    failedId = if (staleFailure) null else state.failedId,
                    failedRes = if (staleFailure) null else state.failedRes
                )
            }
            load(scope)
        }
    }

    fun moveVariantFocus(delta: Int) {
        _state.update { state ->
            val size = state.variantAssetNames.size
            if (size == 0) return@update state
            state.copy(variantFocusIndex = (state.variantFocusIndex + delta).mod(size))
        }
    }

    fun dismissVariantPicker() {
        pendingVariantAssets = emptyList()
        pendingVariantRowId = null
        manager.clearJob()
        _state.update { it.copy(showVariantPicker = false, variantAssetNames = emptyList()) }
    }

    fun confirmVariant(scope: CoroutineScope) {
        val index = _state.value.variantFocusIndex
        val asset = pendingVariantAssets.getOrNull(index) ?: return
        val rowId = pendingVariantRowId ?: return
        _state.update { it.copy(showVariantPicker = false, variantAssetNames = emptyList()) }
        scope.launch {
            val entity = withContext(Dispatchers.IO) { repository.getById(rowId) } ?: return@launch
            manager.install(entity, asset)
        }
    }

    fun selectVariantAt(scope: CoroutineScope, index: Int) {
        _state.update { it.copy(variantFocusIndex = index) }
        confirmVariant(scope)
    }

    fun reconcilePendingInstall() = manager.reconcilePendingInstall()

    fun openImeSettings() = manager.openImeSettings()

    fun showImePicker() = manager.showImePicker()

    private fun applyJobState(rowId: Long, jobState: InstallerJobState) {
        when (jobState) {
            InstallerJobState.Idle ->
                _state.update { it.copy(busyId = null, statusRes = null) }

            InstallerJobState.Checking ->
                _state.update {
                    it.copy(
                        busyId = rowId,
                        busyProgress = 0f,
                        statusRes = R.string.settings_installers_status_checking,
                        failedId = null,
                        failedRes = null
                    )
                }

            is InstallerJobState.Downloading ->
                _state.update {
                    it.copy(
                        busyId = rowId,
                        busyProgress = jobState.progress,
                        statusRes = R.string.settings_installers_status_downloading
                    )
                }

            InstallerJobState.WaitingForInstall ->
                _state.update {
                    it.copy(
                        busyId = rowId,
                        statusRes = R.string.settings_installers_status_installing
                    )
                }

            is InstallerJobState.NeedsVariantChoice -> {
                pendingVariantAssets = jobState.assets
                pendingVariantRowId = rowId
                _state.update {
                    it.copy(
                        busyId = null,
                        statusRes = null,
                        showVariantPicker = true,
                        variantFocusIndex = 0,
                        variantAssetNames = jobState.assets.map { it.name }
                    )
                }
            }

            is InstallerJobState.Failed ->
                _state.update {
                    it.copy(
                        busyId = null,
                        busyProgress = 0f,
                        statusRes = null,
                        failedId = rowId,
                        failedRes = failureRes(jobState.reason)
                    )
                }
        }
    }

    private fun toRow(entity: ManagedInstallerEntity): ManagedInstallerRow {
        val installed = manager.isInstalled(entity.packageName)
        val isKeyboard = SeededInstallers.isControllerKeyboard(entity)
        val packageName = entity.packageName
        return ManagedInstallerRow(
            id = entity.id,
            displayName = entity.displayName,
            repoLabel = "${entity.repoOwner}/${entity.repoName}",
            packageName = packageName,
            installed = installed,
            locked = entity.locked,
            updateAvailable = installed &&
                entity.tagAtInstall != null &&
                entity.latestSeenTag != null &&
                entity.latestSeenTag != entity.tagAtInstall,
            tagAtInstall = entity.tagAtInstall,
            latestSeenTag = entity.latestSeenTag,
            imeEnabled = isKeyboard && packageName != null && manager.isImeEnabled(packageName),
            imeActive = isKeyboard && packageName != null && manager.isImeActive(packageName)
        )
    }

    companion object {
        @StringRes
        fun failureRes(reason: InstallerFailure): Int = when (reason) {
            InstallerFailure.NO_RELEASE -> R.string.settings_installers_error_no_release
            InstallerFailure.NO_APK -> R.string.settings_installers_error_no_apk
            InstallerFailure.DOWNLOAD -> R.string.settings_installers_error_download
            InstallerFailure.RATE_LIMITED -> R.string.settings_installers_error_rate_limited
            InstallerFailure.NOT_FOUND -> R.string.settings_installers_error_not_found
            InstallerFailure.WRONG_PACKAGE -> R.string.settings_installers_error_wrong_package
        }
    }
}
