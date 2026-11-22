package com.nendo.argosy.ui.common.achievements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.core.game.AchievementUi
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.nendo.argosy.data.repository.RAWebApiKeyCheck
import com.nendo.argosy.data.repository.RAWebApiKeyRepository
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.domain.model.AchievementComments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CommentsKeyStatus { CHECKING, REJECTED, UNREACHABLE, NOT_SIGNED_IN }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AchievementCommentsEntryPoint {
    fun achievementCommentsFactory(): AchievementCommentsController.Factory
}

/**
 * A comments controller owned by the calling composition, for a surface with no ViewModel.
 */
@Composable
fun rememberAchievementCommentsController(): AchievementCommentsController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        EntryPointAccessors.fromApplication(context.applicationContext, AchievementCommentsEntryPoint::class.java)
            .achievementCommentsFactory()
            .create(scope)
    }
}

/**
 * The open comments view. [content] is null while loading; [scrollIndex] is the comment the list
 * keeps in view.
 */
data class AchievementCommentsState(
    val achievement: AchievementUi,
    val content: AchievementComments? = null,
    val scrollIndex: Int = 0,
    val keyEntryOpen: Boolean = false,
    val keyDraft: String = "",
    val keyStatus: CommentsKeyStatus? = null
) {
    val commentCount: Int
        get() = (content as? AchievementComments.Loaded)?.comments?.size ?: 0

    val needsKey: Boolean
        get() = content == AchievementComments.NeedsKey || content == AchievementComments.KeyRejected
}

/**
 * Drives one surface's comments view. Every surface that lists achievements owns one, so the
 * game page, the in-game menu and the companion dashboard load, scroll and take a key the same way.
 */
class AchievementCommentsController private constructor(
    private val source: RaMetadataSource,
    private val keyRepository: RAWebApiKeyRepository,
    private val scope: CoroutineScope
) {
    class Factory @Inject constructor(
        private val source: RaMetadataSource,
        private val keyRepository: RAWebApiKeyRepository
    ) {
        fun create(scope: CoroutineScope) = AchievementCommentsController(source, keyRepository, scope)
    }

    private val _state = MutableStateFlow<AchievementCommentsState?>(null)
    val state: StateFlow<AchievementCommentsState?> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var keyJob: Job? = null

    fun open(achievement: AchievementUi) {
        _state.value = AchievementCommentsState(achievement)
        load(achievement)
    }

    fun close() {
        loadJob?.cancel()
        keyJob?.cancel()
        _state.value = null
    }

    fun retry() {
        val achievement = _state.value?.achievement ?: return
        _state.update { it?.copy(content = null, scrollIndex = 0) }
        load(achievement)
    }

    fun scroll(delta: Int) {
        _state.update { state ->
            state?.copy(scrollIndex = (state.scrollIndex + delta).coerceIn(0, (state.commentCount - 1).coerceAtLeast(0)))
        }
    }

    fun openKeyEntry() {
        _state.update { it?.copy(keyEntryOpen = true, keyStatus = null) }
    }

    fun closeKeyEntry() {
        keyJob?.cancel()
        _state.update { it?.copy(keyEntryOpen = false, keyStatus = null) }
    }

    fun setKeyDraft(text: String) {
        _state.update { it?.copy(keyDraft = text, keyStatus = it.keyStatus.takeIf { status -> status == CommentsKeyStatus.CHECKING }) }
    }

    fun submitKey() {
        val current = _state.value ?: return
        if (current.keyStatus == CommentsKeyStatus.CHECKING || current.keyDraft.isBlank()) return
        _state.update { it?.copy(keyStatus = CommentsKeyStatus.CHECKING) }
        keyJob = scope.launch {
            val status = when (keyRepository.validateAndSave(current.keyDraft)) {
                RAWebApiKeyCheck.SAVED -> null
                RAWebApiKeyCheck.REJECTED, RAWebApiKeyCheck.EMPTY -> CommentsKeyStatus.REJECTED
                RAWebApiKeyCheck.UNREACHABLE -> CommentsKeyStatus.UNREACHABLE
                RAWebApiKeyCheck.NOT_SIGNED_IN -> CommentsKeyStatus.NOT_SIGNED_IN
            }
            if (status != null) {
                _state.update { it?.copy(keyStatus = status) }
                return@launch
            }
            _state.update { it?.copy(keyEntryOpen = false, keyDraft = "", keyStatus = null, content = null) }
            load(current.achievement)
        }
    }

    private fun load(achievement: AchievementUi) {
        loadJob?.cancel()
        loadJob = scope.launch {
            val comments = source.achievementComments(achievement.raId)
            _state.update { state ->
                if (state?.achievement?.raId == achievement.raId) state.copy(content = comments, scrollIndex = 0) else state
            }
        }
    }
}
