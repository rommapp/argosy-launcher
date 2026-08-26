package com.nendo.argosy.ui.screens.media.delegates

import com.nendo.argosy.data.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The run of titles the shoulder buttons walk on a media detail surface.
 *
 * The run is the library's own query rather than a remembered list of whatever surface the user
 * came from, so the order here is the order the grid showed and the two cannot drift. The
 * single-screen detail screen and the dual-screen information panel both source their run from
 * this one place. A title with no library has no run; callers refuse the step rather than invent
 * a neighbour.
 */
@Singleton
class MediaSiblingsDelegate @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend fun libraryIdOf(itemId: String): String? =
        mediaRepository.getItem(itemId)?.libraryId

    fun siblingIdsFlow(libraryId: String): Flow<List<String>> =
        mediaRepository.observeLibraryItems(libraryId)
            .map { entities -> entities.map { it.itemId } }
            .distinctUntilChanged()
}
