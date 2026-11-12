package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.setup.SetupCoreDownloads
import com.nendo.argosy.ui.components.PLATFORM_HEADER_COUNT
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SEARCH
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SORT
import com.nendo.argosy.util.PlatformFilterLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The platform list and the built-in emulator offer on the platforms page.
 */
class SetupPlatformsDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformRepository: PlatformRepository,
    private val coreDownloads: SetupCoreDownloads
) {
    private val _state = MutableStateFlow(SetupPlatformsState())
    val state: StateFlow<SetupPlatformsState> = _state.asStateFlow()

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private lateinit var scope: CoroutineScope

    fun bind(scope: CoroutineScope) {
        this.scope = scope
    }

    /**
     * Fetches the library's platforms and the emulators they lack, and answers whether the
     * platforms page is offered. A library above the threshold starts with every platform off so
     * the person picks; a smaller one syncs them all.
     */
    suspend fun load(platformCount: Int): Boolean {
        val defaultSyncEnabled = platformCount <= PLATFORM_SELECT_THRESHOLD
        val all = when (val result = romMRepository.fetchAndStorePlatforms(defaultSyncEnabled)) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> platformRepository.observeAllPlatforms().first()
        }
        val missing = missingFor(all)
        _state.update { it.copy(all = all, shown = filtered(all, it), missingCoreIds = missing, loaded = true) }
        return offersPlatforms(maxOf(platformCount, all.size), missing.size)
    }

    fun toggle(platformId: Long) {
        val platform = _state.value.all.firstOrNull { it.id == platformId } ?: return
        update(listOf(platform), !platform.syncEnabled)
    }

    fun toggleAll() {
        val all = _state.value.all
        update(all, !all.all { it.syncEnabled })
    }

    fun toggleDownloadCores() {
        _state.update { it.copy(downloadCores = !it.downloadCores) }
    }

    fun startCoreDownloads() {
        val current = _state.value
        if (current.downloadCores) coreDownloads.start(current.missingCoreIds)
    }

    fun moveHeader(delta: Int) {
        _state.update { it.copy(headerIndex = (it.headerIndex + delta).mod(PLATFORM_HEADER_COUNT)) }
    }

    fun moveSortMenu(delta: Int) {
        _state.update {
            it.copy(sortMenuIndex = (it.sortMenuIndex + delta).mod(PlatformFilterLogic.SortMode.entries.size))
        }
    }

    fun confirmHeader() {
        when (_state.value.headerIndex) {
            PLATFORM_HEADER_SEARCH -> openSearch()
            PLATFORM_HEADER_SORT -> openSortMenu()
            else -> cycleFilterMode()
        }
    }

    fun confirmSortMenu() {
        val mode = PlatformFilterLogic.SortMode.entries[_state.value.sortMenuIndex]
        _state.update { it.copy(sortMenuOpen = false) }
        setSortMode(mode)
    }

    fun setSortMode(mode: PlatformFilterLogic.SortMode) {
        _state.update { it.copy(sortMode = mode, sortMenuOpen = false) }
        refilter()
    }

    fun cycleFilterMode() {
        _state.update {
            val next = when (it.filterMode) {
                PlatformFilterLogic.FilterMode.ALL -> PlatformFilterLogic.FilterMode.HAS_GAMES
                PlatformFilterLogic.FilterMode.HAS_GAMES -> PlatformFilterLogic.FilterMode.ENABLED
                PlatformFilterLogic.FilterMode.ENABLED -> PlatformFilterLogic.FilterMode.ALL
            }
            it.copy(filterMode = next)
        }
        refilter()
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        refilter()
    }

    fun openSearch() = _state.update { it.copy(headerIndex = PLATFORM_HEADER_SEARCH, searchActive = true) }

    fun closeSearch() = _state.update { it.copy(searchActive = false) }

    fun openSortMenu() = _state.update {
        it.copy(
            headerIndex = PLATFORM_HEADER_SORT,
            sortMenuOpen = true,
            sortMenuIndex = PlatformFilterLogic.SortMode.entries.indexOf(it.sortMode).coerceAtLeast(0)
        )
    }

    fun closeSortMenu() = _state.update { it.copy(sortMenuOpen = false) }

    private fun update(platforms: List<PlatformEntity>, enabled: Boolean) {
        val ids = platforms.map { it.id }.toSet()
        _state.update { current ->
            val all = current.all.map { if (it.id in ids) it.copy(syncEnabled = enabled) else it }
            current.copy(all = all, shown = filtered(all, current))
        }
        scope.launch {
            ids.forEach { platformRepository.updateSyncEnabled(it, enabled) }
            val missing = missingFor(_state.value.all)
            _state.update { it.copy(missingCoreIds = missing) }
        }
    }

    private fun refilter() {
        _state.update { it.copy(shown = filtered(it.all, it)) }
    }

    private suspend fun missingFor(all: List<PlatformEntity>): List<String> = withContext(ioDispatcher) {
        coreDownloads.missingCoreIds(all.filter { it.syncEnabled }.map { it.slug }.toSet())
    }

    private fun filtered(all: List<PlatformEntity>, state: SetupPlatformsState): List<PlatformEntity> =
        PlatformFilterLogic.filterAndSortPlatformEntities(
            items = all,
            searchQuery = state.searchQuery,
            filterMode = state.filterMode,
            sortMode = state.sortMode
        )
}
