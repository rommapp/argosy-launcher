package com.nendo.argosy.ui.screens.quaypass

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.QuayPassOwnedPartDao
import com.nendo.argosy.data.local.entity.QuayPassOwnedPartEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPartCatalog
import com.nendo.argosy.ui.quaypass.avatar.QuayPassPartPricing
import com.nendo.argosy.ui.quaypass.avatar.colorIndexFor
import com.nendo.argosy.ui.quaypass.avatar.partIndexFor
import com.nendo.argosy.ui.quaypass.avatar.withColor
import com.nendo.argosy.ui.quaypass.avatar.withPart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

const val GRID_COLUMNS = 4
const val GRID_PAGE_SIZE = 16
const val GRID_THRESHOLD = 16

@HiltViewModel
class QuayPassAvatarCustomizerViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val ownedPartDao: QuayPassOwnedPartDao,
    private val socialService: ArgosSocialService,
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
        viewModelScope.launch {
            ownedPartDao.observeOwnedKeys().collect { keys ->
                _state.update { it.copy(ownedParts = keys.toSet()) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.userPreferences
                .map { it.quayPassTicketBalance }
                .collect { balance -> _state.update { it.copy(ticketBalance = balance) } }
        }
    }

    private suspend fun loadInitial() {
        val prefs = preferencesRepository.userPreferences.first()
        val avatar = decodeAvatar(prefs.quayPassAvatarBytes) ?: defaultAvatar()
        _state.update {
            it.copy(
                avatar = avatar,
                selectedCategory = AvatarCategory.Hair,
                gridPage = pageForSelected(AvatarCategory.Hair, avatar)
            )
        }
    }

    fun selectCategory(category: AvatarCategory) {
        _state.update {
            it.copy(
                selectedCategory = category,
                gridPage = pageForSelected(category, it.avatar),
                toggleFocus = 0
            )
        }
    }

    fun selectPartIndex(category: AvatarCategory, index: Int) {
        applyPart(category, index)
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

    fun pageStep(direction: Int) {
        _state.update { current ->
            if (!current.selectedCategory.usesGrid(partCatalog)) return@update current
            val pages = current.selectedCategory.pageCount(partCatalog)
            current.copy(gridPage = (current.gridPage + direction).mod(pages))
        }
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
            CustomizerSection.Toggles -> stepToggleFocus(direction)
            CustomizerSection.Actions -> stepActionFocus(direction)
        }
    }

    fun confirmFocused() {
        val current = _state.value
        when (current.focusedSection) {
            CustomizerSection.Toggles -> toggleFocused()
            CustomizerSection.Actions -> if (current.actionFocus == 1) save()
            else -> Unit
        }
    }

    fun requestPurchaseFocused() {
        val pending = _state.value.pendingPurchase ?: return
        confirmPurchase(pending)
    }

    fun confirmPurchase(request: PurchaseRequest) {
        viewModelScope.launch {
            val key = QuayPassPartPricing.partKey(request.category, request.index)
            ownedPartDao.upsert(QuayPassOwnedPartEntity(partKey = key, acquiredAt = Instant.now(), synced = false))
            _state.update {
                it.copy(avatar = it.avatar.withPart(request.category, request.index), pendingPurchase = null)
            }
            socialService.purchaseQuayPassPart(key)
        }
    }

    fun dismissPurchase() {
        _state.update { it.copy(pendingPurchase = null) }
    }

    private fun applyPart(category: AvatarCategory, index: Int) {
        val s = _state.value
        if (!QuayPassPartPricing.isUnlocked(category, index, s.ownedParts)) {
            _state.update {
                it.copy(
                    pendingPurchase = PurchaseRequest(
                        category = category,
                        index = index,
                        cost = QuayPassPartPricing.costFor(category, index)
                    )
                )
            }
            return
        }
        _state.update { it.copy(avatar = it.avatar.withPart(category, index)) }
    }

    private fun stepCategory(direction: Int) {
        _state.update {
            val cats = AvatarCategory.entries
            val next = cats[(cats.indexOf(it.selectedCategory) + direction).mod(cats.size)]
            it.copy(
                selectedCategory = next,
                gridPage = pageForSelected(next, it.avatar),
                toggleFocus = 0
            )
        }
    }

    private fun stepPart(direction: Int) {
        val s = _state.value
        val parts = partCatalog.forCategory(s.selectedCategory)
        if (parts.isEmpty()) return
        val current = s.avatar.partIndexFor(s.selectedCategory)
        val currentIdx = parts.indexOf(current).coerceAtLeast(0)
        val next = parts[(currentIdx + direction).mod(parts.size)]
        applyPart(s.selectedCategory, next)
        if (_state.value.selectedCategory.usesGrid(partCatalog)) {
            _state.update { it.copy(gridPage = next.gridPageWithin(parts)) }
        }
    }

    private fun stepColor(direction: Int) {
        val s = _state.value
        val current = s.avatar.colorIndexFor(s.selectedCategory)
        val next = (current + direction).mod(16)
        _state.update { it.copy(avatar = it.avatar.withColor(s.selectedCategory, next)) }
    }

    private fun stepToggleFocus(direction: Int) {
        _state.update {
            val count = toggleCount(it.selectedCategory)
            if (count <= 1) return@update it
            it.copy(toggleFocus = (it.toggleFocus + direction).mod(count))
        }
    }

    private fun toggleFocused() {
        val s = _state.value
        when (visibleToggles(s.selectedCategory).getOrNull(s.toggleFocus)) {
            CustomizerToggle.FlipHair -> setFlipHair(!s.avatar.flipHair)
            CustomizerToggle.Mole -> setMoleEnabled(!s.avatar.moleEnabled)
            null -> Unit
        }
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
        add(CustomizerSection.Toggles)
        add(CustomizerSection.Actions)
    }

    private fun pageForSelected(category: AvatarCategory, avatar: QuayPassAvatar): Int {
        if (!category.usesGrid(partCatalog)) return 0
        return avatar.partIndexFor(category).gridPageWithin(partCatalog.forCategory(category))
    }

    private fun toggleCount(category: AvatarCategory): Int = visibleToggles(category).size

    fun cancel(): Boolean {
        if (_state.value.pendingPurchase != null) {
            dismissPurchase()
            return true
        }
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

enum class CustomizerSection { Category, Parts, Color, Toggles, Actions }

enum class CustomizerToggle { FlipHair, Mole }

data class PurchaseRequest(val category: AvatarCategory, val index: Int, val cost: Int)

data class CustomizerState(
    val avatar: QuayPassAvatar = QuayPassAvatar(),
    val selectedCategory: AvatarCategory = AvatarCategory.Face,
    val focusedSection: CustomizerSection = CustomizerSection.Category,
    val actionFocus: Int = 1,
    val toggleFocus: Int = 0,
    val gridPage: Int = 0,
    val ownedParts: Set<String> = emptySet(),
    val ticketBalance: Int = 0,
    val pendingPurchase: PurchaseRequest? = null
)

fun visibleToggles(category: AvatarCategory): List<CustomizerToggle> = buildList {
    if (category == AvatarCategory.Hair) add(CustomizerToggle.FlipHair)
    add(CustomizerToggle.Mole)
}

fun AvatarCategory.usesGrid(catalog: QuayPassAvatarPartCatalog): Boolean =
    catalog.forCategory(this).size > GRID_THRESHOLD

fun AvatarCategory.pageCount(catalog: QuayPassAvatarPartCatalog): Int {
    val size = catalog.forCategory(this).size
    return if (size == 0) 1 else (size + GRID_PAGE_SIZE - 1) / GRID_PAGE_SIZE
}

private fun Int.gridPageWithin(indices: List<Int>): Int {
    val pos = indices.indexOf(this).coerceAtLeast(0)
    return pos / GRID_PAGE_SIZE
}
