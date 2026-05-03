package com.nendo.argosy.ui.screens.quaypass

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPartCatalog
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

data class CustomizerState(
    val avatar: QuayPassAvatar = QuayPassAvatar(),
    val selectedCategory: AvatarCategory = AvatarCategory.Hair
)

private fun QuayPassAvatar.withPart(category: AvatarCategory, index: Int): QuayPassAvatar = when (category) {
    AvatarCategory.Face -> copy(faceShape = index)
    AvatarCategory.Wrinkles -> copy(wrinkles = index)
    AvatarCategory.Makeup -> copy(makeup = index)
    AvatarCategory.Eyes -> copy(eyeType = index)
    AvatarCategory.Eyebrows -> copy(eyebrowType = index)
    AvatarCategory.Nose -> copy(noseType = index)
    AvatarCategory.Mouth -> copy(mouthType = index)
    AvatarCategory.Mustache -> copy(mustacheType = index)
    AvatarCategory.Goatee -> copy(goateeType = index)
    AvatarCategory.Hair -> copy(hairType = index)
    AvatarCategory.Glasses -> copy(glassesType = index)
    AvatarCategory.Hat -> copy(hatType = index)
}

private fun QuayPassAvatar.withColor(category: AvatarCategory, colorIndex: Int): QuayPassAvatar {
    val c = colorIndex.coerceIn(0, 15)
    return when (category) {
        AvatarCategory.Face -> copy(skinColor = c)
        AvatarCategory.Hair -> copy(hairColor = c)
        AvatarCategory.Eyes -> copy(eyeColor = c)
        AvatarCategory.Eyebrows -> copy(eyebrowColor = c)
        AvatarCategory.Mouth -> copy(mouthColor = c)
        AvatarCategory.Mustache, AvatarCategory.Goatee -> copy(facialHairColor = c)
        AvatarCategory.Glasses -> copy(glassesColor = c)
        AvatarCategory.Hat -> copy(hatColor = c)
        else -> this
    }
}
