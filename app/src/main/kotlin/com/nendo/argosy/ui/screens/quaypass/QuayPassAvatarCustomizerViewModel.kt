package com.nendo.argosy.ui.screens.quaypass

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPartCatalog
import com.nendo.argosy.ui.quaypass.avatar.colorIndexFor
import com.nendo.argosy.ui.quaypass.avatar.partIndexFor
import com.nendo.argosy.ui.quaypass.avatar.withColor
import com.nendo.argosy.ui.quaypass.avatar.withPart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuayPassAvatarCustomizerViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    val partCatalog: QuayPassAvatarPartCatalog
) : ViewModel() {

    private val _state = MutableStateFlow(CustomizerState())
    val state: StateFlow<CustomizerState> = _state.asStateFlow()

    private val _events = kotlinx.coroutines.flow.MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events

    sealed class Event {
        data object Saved : Event()
        data object Cancelled : Event()
        data class Error(val message: String) : Event()
    }

    init {
        viewModelScope.launch { loadInitial() }
    }

    private suspend fun loadInitial() {
        val prefs = preferencesRepository.userPreferences.first()
        val avatar = decodeAvatar(prefs.quayPassAvatarBytes) ?: defaultAvatar()
        _state.update { CustomizerState(avatar = avatar, selectedCategory = AvatarCategory.Hair) }
    }

    fun selectCategory(category: AvatarCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun selectPartIndex(category: AvatarCategory, index: Int) {
        _state.update { it.copy(avatar = it.avatar.withPart(category, index)) }
    }

    fun selectColor(category: AvatarCategory, colorIndex: Int) {
        _state.update { it.copy(avatar = it.avatar.withColor(category, colorIndex)) }
    }

    fun setFavoriteColor(index: Int) {
        _state.update { it.copy(avatar = it.avatar.copy(favoriteColor = index.coerceIn(0, 15))) }
    }

    fun setFlipHair(flip: Boolean) {
        _state.update { it.copy(avatar = it.avatar.copy(flipHair = flip)) }
    }

    fun setMoleEnabled(enabled: Boolean) {
        _state.update { it.copy(avatar = it.avatar.copy(moleEnabled = enabled)) }
    }

    fun save() {
        viewModelScope.launch {
            val avatar = _state.value.avatar
            val bytes = QuayPassAvatarCodec.encode(avatar)
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            preferencesRepository.setQuayPassAvatar(base64)
            _events.tryEmit(Event.Saved)
        }
    }

    fun stepSection(direction: Int) {
        _state.update { current ->
            val sections = visibleSections(current.selectedCategory)
            val currentIdx = sections.indexOf(current.focusedSection).coerceAtLeast(0)
            val nextIdx = (currentIdx + direction).coerceIn(0, sections.lastIndex)
            current.copy(focusedSection = sections[nextIdx])
        }
    }

    fun adjustWithinSection(direction: Int) {
        val current = _state.value
        when (current.focusedSection) {
            CustomizerSection.Category -> stepCategory(direction)
            CustomizerSection.Parts -> stepPart(direction)
            CustomizerSection.Color -> stepColor(direction)
            CustomizerSection.Actions -> stepActionFocus(direction)
        }
    }

    fun confirmFocused() {
        val current = _state.value
        if (current.focusedSection == CustomizerSection.Actions) {
            if (current.actionFocus == 1) save()
        }
    }

    private fun stepCategory(direction: Int) {
        _state.update {
            val cats = AvatarCategory.entries
            val currentIdx = cats.indexOf(it.selectedCategory)
            val nextIdx = ((currentIdx + direction) % cats.size + cats.size) % cats.size
            it.copy(selectedCategory = cats[nextIdx])
        }
    }

    private fun stepPart(direction: Int) {
        val s = _state.value
        val parts = partCatalog.forCategory(s.selectedCategory)
        if (parts.isEmpty()) return
        val current = s.avatar.partIndexFor(s.selectedCategory)
        val currentIdx = parts.indexOf(current).coerceAtLeast(0)
        val next = parts[((currentIdx + direction) % parts.size + parts.size) % parts.size]
        _state.update { it.copy(avatar = it.avatar.withPart(s.selectedCategory, next)) }
    }

    private fun stepColor(direction: Int) {
        val s = _state.value
        val current = s.avatar.colorIndexFor(s.selectedCategory)
        val next = ((current + direction) % 16 + 16) % 16
        _state.update { it.copy(avatar = it.avatar.withColor(s.selectedCategory, next)) }
    }

    private fun stepActionFocus(direction: Int) {
        _state.update {
            it.copy(actionFocus = (it.actionFocus + direction).coerceIn(0, 1))
        }
    }

    private fun visibleSections(category: AvatarCategory): List<CustomizerSection> = buildList {
        add(CustomizerSection.Category)
        add(CustomizerSection.Parts)
        if (category.isTintable()) add(CustomizerSection.Color)
        add(CustomizerSection.Actions)
    }

    fun cancel(): Boolean {
        _events.tryEmit(Event.Cancelled)
        return true
    }

    private fun decodeAvatar(base64: String?): QuayPassAvatar? = base64?.let {
        runCatching {
            QuayPassAvatarCodec.decode(Base64.decode(it, Base64.NO_WRAP))
        }.getOrNull()
    }

    private fun defaultAvatar(): QuayPassAvatar = QuayPassAvatar(
        faceShape = 1,
        skinColor = 1,
        hairType = 1,
        hairColor = 0,
        eyeType = 1,
        eyeColor = 0,
        eyebrowType = 1,
        eyebrowColor = 0,
        noseType = 1,
        mouthType = 1,
        mouthColor = 0,
        favoriteColor = 6
    )
}

enum class CustomizerSection { Category, Parts, Color, Actions }

data class CustomizerState(
    val avatar: QuayPassAvatar = QuayPassAvatar(),
    val selectedCategory: AvatarCategory = AvatarCategory.Face,
    val focusedSection: CustomizerSection = CustomizerSection.Category,
    val actionFocus: Int = 1
)

