package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.CollectionItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionModalDelegate @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val romMRepository: RomMRepository,
    private val soundManager: SoundFeedbackManager
) {
    data class State(
        val isVisible: Boolean = false,
        val gameId: Long = 0,
        val collections: List<CollectionItemUi> = emptyList(),
        val focusIndex: Int = 0,
        val showCreateDialog: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun show(scope: CoroutineScope, gameId: Long) {
        scope.launch {
            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() }
            val gameCollectionIds = collectionRepository.getCollectionIdsForGame(gameId)

            val collectionItems = allCollections.map { collection ->
                CollectionItemUi(
                    id = collection.id,
                    name = collection.name,
                    isInCollection = gameCollectionIds.contains(collection.id)
                )
            }

            _state.update {
                it.copy(
                    isVisible = true,
                    gameId = gameId,
                    collections = collectionItems,
                    focusIndex = 0,
                    showCreateDialog = false
                )
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    fun dismiss() {
        _state.update {
            it.copy(
                isVisible = false,
                showCreateDialog = false
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveFocusUp() {
        _state.update {
            it.copy(focusIndex = (it.focusIndex - 1).coerceAtLeast(0))
        }
    }

    fun moveFocusDown() {
        _state.update {
            val filtered = it.collections.filter { c -> c.name.isNotBlank() }
            val maxIndex = filtered.size
            it.copy(focusIndex = (it.focusIndex + 1).coerceAtMost(maxIndex))
        }
    }

    fun confirmSelection(scope: CoroutineScope): Boolean {
        val state = _state.value
        val index = state.focusIndex
        val filtered = state.collections.filter { it.name.isNotBlank() }

        if (index == filtered.size) {
            showCreateDialog()
            return false
        }

        val collection = filtered.getOrNull(index) ?: return false
        toggleCollection(scope, collection.id)
        return true
    }

    fun toggleCollection(scope: CoroutineScope, collectionId: Long) {
        val state = _state.value
        val gameId = state.gameId
        if (gameId == 0L) return

        scope.launch {
            val isInCollection = state.collections.find { it.id == collectionId }?.isInCollection ?: false
            if (isInCollection) {
                collectionRepository.removeGameFromCollection(collectionId, gameId)
                romMRepository.removeGameFromCollectionWithSync(gameId, collectionId)
            } else {
                collectionRepository.addGameToCollection(
                    CollectionGameEntity(
                        collectionId = collectionId,
                        gameId = gameId
                    )
                )
                romMRepository.addGameToCollectionWithSync(gameId, collectionId)
            }

            val updatedCollections = state.collections.map {
                if (it.id == collectionId) it.copy(isInCollection = !isInCollection) else it
            }
            _state.update { it.copy(collections = updatedCollections) }
        }
    }

    fun showCreateDialog() {
        _state.update { it.copy(showCreateDialog = true) }
    }

    fun hideCreateDialog() {
        _state.update { it.copy(showCreateDialog = false) }
    }

    fun createAndAdd(scope: CoroutineScope, name: String) {
        val gameId = _state.value.gameId
        if (gameId == 0L) return

        scope.launch {
            val collectionId = collectionRepository.insertCollection(
                CollectionEntity(name = name)
            )
            collectionRepository.addGameToCollection(
                CollectionGameEntity(
                    collectionId = collectionId,
                    gameId = gameId
                )
            )
            romMRepository.createCollectionWithSync(name)

            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() }
            val gameCollectionIds = collectionRepository.getCollectionIdsForGame(gameId)

            val collectionItems = allCollections.map { collection ->
                CollectionItemUi(
                    id = collection.id,
                    name = collection.name,
                    isInCollection = gameCollectionIds.contains(collection.id)
                )
            }

            _state.update {
                it.copy(
                    showCreateDialog = false,
                    collections = collectionItems
                )
            }
        }
    }
}
