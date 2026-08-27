package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.speedrun.SpeedrunRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpeedrunCategoryUi(
    val id: Long,
    val name: String,
    val segmentCount: Int,
    val attemptCount: Int
)

sealed class SpeedrunPrompt {
    abstract val title: String

    data class Text(
        override val title: String,
        val initial: String,
        val target: Target
    ) : SpeedrunPrompt() {
        enum class Target { NEW_CATEGORY, RENAME_CATEGORY, NEW_SEGMENT, RENAME_SEGMENT }
    }

    data class ConfirmDelete(override val title: String, val isCategory: Boolean) : SpeedrunPrompt()
}

sealed class SpeedrunImport {
    data object Loading : SpeedrunImport()
    data class Options(val entries: List<com.nendo.argosy.data.speedrun.SeedCategory>, val focusIndex: Int = 0) : SpeedrunImport()
    data object Importing : SpeedrunImport()
    data class Preview(
        val entry: com.nendo.argosy.data.speedrun.SeedCategory,
        val template: com.nendo.argosy.data.speedrun.SeedTemplate,
        val runnerIndex: Int,
        val options: Options
    ) : SpeedrunImport()
    data class Failed(val message: String) : SpeedrunImport()
}

data class SpeedrunSplitsState(
    val visible: Boolean = false,
    val categories: List<SpeedrunCategoryUi> = emptyList(),
    val editingCategory: SpeedrunCategoryUi? = null,
    val segments: List<String> = emptyList(),
    val focusIndex: Int = 0,
    val prompt: SpeedrunPrompt? = null,
    val import: SpeedrunImport? = null
)

class SpeedrunSplitsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speedrunRepository: SpeedrunRepository,
    private val seedService: com.nendo.argosy.data.speedrun.SpeedrunSeedService,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(SpeedrunSplitsState())
    val state: StateFlow<SpeedrunSplitsState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var gameId: Long = -1L
    private var gameTitle: String = ""

    fun open(scope: CoroutineScope, gameId: Long, gameTitle: String) {
        this.scope = scope
        this.gameId = gameId
        this.gameTitle = gameTitle
        _state.value = SpeedrunSplitsState(visible = true)
        soundManager.play(SoundType.OPEN_MODAL)
        reloadCategories()
    }

    fun dismiss() {
        val s = _state.value
        when {
            s.import is SpeedrunImport.Preview -> _state.update {
                it.copy(import = (s.import as SpeedrunImport.Preview).options)
            }
            s.import != null -> _state.update { it.copy(import = null) }
            s.prompt != null -> _state.update { it.copy(prompt = null) }
            s.editingCategory != null -> _state.update {
                it.copy(editingCategory = null, segments = emptyList(), focusIndex = 0)
            }
            else -> {
                _state.value = SpeedrunSplitsState()
                soundManager.play(SoundType.CLOSE_MODAL)
            }
        }
    }

    fun openImport() {
        val currentScope = scope ?: return
        if (gameTitle.isBlank()) return
        _state.update { it.copy(import = SpeedrunImport.Loading) }
        currentScope.launch(Dispatchers.IO) {
            val entries = seedService.fetchSeedCategories(gameTitle)
            _state.update {
                it.copy(
                    import = if (entries.isEmpty()) {
                        SpeedrunImport.Failed(
                            context.getString(
                                R.string.gamedetail_speedrun_import_none_found,
                                gameTitle
                            )
                        )
                    } else {
                        SpeedrunImport.Options(entries)
                    }
                )
            }
        }
    }

    fun moveImportFocus(delta: Int) {
        _state.update { s ->
            val import = s.import
            if (import !is SpeedrunImport.Options || import.entries.isEmpty()) s
            else s.copy(import = import.copy(focusIndex = (import.focusIndex + delta).mod(import.entries.size)))
        }
    }

    fun confirmImportAt(index: Int) {
        _state.update { s ->
            val import = s.import
            if (import is SpeedrunImport.Options) s.copy(import = import.copy(focusIndex = index)) else s
        }
        confirmImport()
    }

    fun confirmImport() {
        val s = _state.value
        when (val import = s.import) {
            is SpeedrunImport.Options -> {
                val entry = import.entries.getOrNull(import.focusIndex) ?: return
                when (entry.source) {
                    com.nendo.argosy.data.speedrun.SeedCategory.Source.THERUN -> loadPreview(entry, import, 0, 1)
                    com.nendo.argosy.data.speedrun.SeedCategory.Source.SPEEDRUN_COM -> {
                        val currentScope = scope ?: return
                        _state.update { it.copy(import = SpeedrunImport.Importing) }
                        currentScope.launch(Dispatchers.IO) {
                            speedrunRepository.createCategory(
                                gameId = gameId,
                                name = entry.label,
                                segmentNames = listOf(context.getString(R.string.gamedetail_speedrun_default_segment_name)),
                                sourceLabel = "speedrun.com"
                            )
                            _state.update { it.copy(import = null) }
                            reloadCategories()
                        }
                    }
                }
            }
            is SpeedrunImport.Preview -> {
                val currentScope = scope ?: return
                _state.update { it.copy(import = SpeedrunImport.Importing) }
                currentScope.launch(Dispatchers.IO) {
                    speedrunRepository.createCategory(
                        gameId = gameId,
                        name = import.entry.label,
                        segmentNames = import.template.segments,
                        sourceLabel = "therun.gg: ${import.template.runnerUsername}"
                    )
                    _state.update { it.copy(import = null) }
                    reloadCategories()
                }
            }
            is SpeedrunImport.Failed -> _state.update { it.copy(import = null) }
            else -> Unit
        }
    }

    fun cyclePreviewRunner(delta: Int) {
        val import = _state.value.import as? SpeedrunImport.Preview ?: return
        loadPreview(import.entry, import.options, import.runnerIndex + delta, delta, fallback = import)
    }

    private fun loadPreview(
        entry: com.nendo.argosy.data.speedrun.SeedCategory,
        options: SpeedrunImport.Options,
        startIndex: Int,
        direction: Int,
        fallback: SpeedrunImport.Preview? = null
    ) {
        val currentScope = scope ?: return
        if (startIndex !in entry.leaderboardUsernames.indices) return
        _state.update { it.copy(import = SpeedrunImport.Importing) }
        currentScope.launch(Dispatchers.IO) {
            val result = seedService.fetchTheRunTemplateAt(entry, startIndex, direction)
            _state.update {
                it.copy(
                    import = when {
                        result != null -> SpeedrunImport.Preview(entry, result.second, result.first, options)
                        fallback != null -> fallback
                        else -> SpeedrunImport.Failed(
                            context.getString(
                                R.string.gamedetail_speedrun_import_fetch_failed,
                                entry.label
                            )
                        )
                    }
                )
            }
        }
    }

    fun moveFocus(delta: Int) {
        val s = _state.value
        val itemCount = if (s.editingCategory != null) s.segments.size + 2 else s.categories.size + 1
        if (itemCount == 0) return
        _state.update { it.copy(focusIndex = (it.focusIndex + delta).mod(itemCount)) }
    }

    fun confirmFocusedAt(index: Int) {
        _state.update { it.copy(focusIndex = index) }
        confirmFocused()
    }

    fun confirmFocused() {
        val s = _state.value
        if (s.prompt != null) return
        val editing = s.editingCategory
        if (editing != null) {
            when {
                s.focusIndex < s.segments.size -> {
                    val segment = s.segments[s.focusIndex]
                    _state.update {
                        it.copy(
                            prompt = SpeedrunPrompt.Text(
                                context.getString(
                                    R.string.gamedetail_speedrun_prompt_rename_segment
                                ),
                                segment,
                                SpeedrunPrompt.Text.Target.RENAME_SEGMENT
                            )
                        )
                    }
                }
                s.focusIndex == s.segments.size -> _state.update {
                    it.copy(
                        prompt = SpeedrunPrompt.Text(
                            context.getString(
                                R.string.gamedetail_speedrun_prompt_rename_category
                            ),
                            editing.name,
                            SpeedrunPrompt.Text.Target.RENAME_CATEGORY
                        )
                    )
                }
                else -> _state.update {
                    it.copy(prompt = SpeedrunPrompt.ConfirmDelete(editing.name, isCategory = true))
                }
            }
        } else {
            if (s.focusIndex == s.categories.size) {
                openImport()
                return
            }
            val category = s.categories.getOrNull(s.focusIndex) ?: return
            loadSegments(category)
        }
    }

    fun promptNew() {
        val s = _state.value
        if (s.prompt != null) return
        _state.update {
            if (s.editingCategory != null) {
                it.copy(
                    prompt = SpeedrunPrompt.Text(
                        context.getString(R.string.gamedetail_speedrun_prompt_new_segment),
                        "",
                        SpeedrunPrompt.Text.Target.NEW_SEGMENT
                    )
                )
            } else {
                it.copy(
                    prompt = SpeedrunPrompt.Text(
                        context.getString(R.string.gamedetail_speedrun_prompt_new_category),
                        "",
                        SpeedrunPrompt.Text.Target.NEW_CATEGORY
                    )
                )
            }
        }
    }

    fun promptDelete() {
        val s = _state.value
        if (s.prompt != null || s.editingCategory == null) return
        val segment = s.segments.getOrNull(s.focusIndex) ?: return
        if (s.segments.size <= 1) return
        _state.update { it.copy(prompt = SpeedrunPrompt.ConfirmDelete(segment, isCategory = false)) }
    }

    fun moveSegment(delta: Int) {
        val s = _state.value
        if (s.editingCategory == null || s.prompt != null) return
        val from = s.focusIndex
        if (from !in s.segments.indices) return
        val to = from + delta
        if (to !in s.segments.indices) return
        val reordered = s.segments.toMutableList().also {
            val item = it.removeAt(from)
            it.add(to, item)
        }
        _state.update { it.copy(segments = reordered, focusIndex = to) }
        persistSegments(s.editingCategory.id, reordered)
    }

    fun confirmPrompt(text: String) {
        val s = _state.value
        when (val prompt = s.prompt) {
            is SpeedrunPrompt.Text -> {
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return
                when (prompt.target) {
                    SpeedrunPrompt.Text.Target.NEW_CATEGORY -> createCategory(trimmed)
                    SpeedrunPrompt.Text.Target.RENAME_CATEGORY -> renameCategory(trimmed)
                    SpeedrunPrompt.Text.Target.NEW_SEGMENT -> addSegment(trimmed)
                    SpeedrunPrompt.Text.Target.RENAME_SEGMENT -> renameSegment(trimmed)
                }
            }
            is SpeedrunPrompt.ConfirmDelete -> if (prompt.isCategory) deleteCategory() else deleteSegment()
            null -> Unit
        }
    }

    fun dismissPrompt() {
        _state.update { it.copy(prompt = null) }
    }

    private fun createCategory(name: String) {
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            speedrunRepository.createCategory(
                gameId,
                name,
                listOf(context.getString(R.string.gamedetail_speedrun_default_segment_name))
            )
            reloadCategories(clearPrompt = true)
        }
    }

    private fun renameCategory(name: String) {
        val s = _state.value
        val category = s.editingCategory ?: return
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            speedrunRepository.renameCategory(category.id, name)
            _state.update { it.copy(editingCategory = category.copy(name = name), prompt = null) }
            reloadCategories()
        }
    }

    private fun deleteCategory() {
        val s = _state.value
        val category = s.editingCategory ?: return
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            speedrunRepository.deleteCategory(category.id)
            _state.update {
                it.copy(editingCategory = null, segments = emptyList(), focusIndex = 0, prompt = null)
            }
            reloadCategories()
        }
    }

    private fun addSegment(name: String) {
        val s = _state.value
        val category = s.editingCategory ?: return
        val updated = s.segments + name
        _state.update { it.copy(segments = updated, prompt = null, focusIndex = updated.lastIndex) }
        persistSegments(category.id, updated)
    }

    private fun renameSegment(name: String) {
        val s = _state.value
        val category = s.editingCategory ?: return
        val updated = s.segments.toMutableList().also { it[s.focusIndex] = name }
        _state.update { it.copy(segments = updated, prompt = null) }
        persistSegments(category.id, updated)
    }

    private fun deleteSegment() {
        val s = _state.value
        val category = s.editingCategory ?: return
        if (s.segments.size <= 1) return
        val updated = s.segments.toMutableList().also { it.removeAt(s.focusIndex) }
        _state.update {
            it.copy(segments = updated, prompt = null, focusIndex = it.focusIndex.coerceAtMost(updated.lastIndex))
        }
        persistSegments(category.id, updated)
    }

    private fun loadSegments(category: SpeedrunCategoryUi) {
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            val segments = speedrunRepository.getSegmentNames(category.id)
            _state.update {
                it.copy(editingCategory = category, segments = segments, focusIndex = 0)
            }
        }
    }

    private fun persistSegments(categoryId: Long, segments: List<String>) {
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            speedrunRepository.replaceSegments(categoryId, segments)
        }
    }

    private fun reloadCategories(clearPrompt: Boolean = false) {
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            val categories = speedrunRepository.getCategoriesForGame(gameId).map { category ->
                val segments = speedrunRepository.getSegmentNames(category.id)
                val comparison = speedrunRepository.getComparison(category.id, segments.size)
                SpeedrunCategoryUi(
                    id = category.id,
                    name = category.name,
                    segmentCount = segments.size,
                    attemptCount = comparison.attemptCount
                )
            }
            _state.update {
                it.copy(
                    categories = categories,
                    focusIndex = it.focusIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0)),
                    prompt = if (clearPrompt) null else it.prompt
                )
            }
        }
    }
}
