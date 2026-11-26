package com.nendo.argosy.ui.screens.collections

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.toListItem
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.screens.library.ActiveFilters
import com.nendo.argosy.ui.screens.library.FilterCategory
import com.nendo.argosy.ui.screens.library.FilterMemoryScope
import com.nendo.argosy.ui.screens.library.LibraryFilterMemory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSetBrowserTest {

    private val entities = listOf(
        entity(1, "Chrono Trigger", genre = "RPG"),
        entity(2, "Bomberman", genre = "Action"),
        entity(3, "Earthbound", genre = "RPG"),
        entity(4, "Hidden Gem", genre = "RPG")
    )

    private val gameRepository = mockk<GameRepository>(relaxed = true) {
        every { observeHiddenList() } returns flowOf(emptyList())
    }
    private val collectionRepository = mockk<CollectionRepository>(relaxed = true) {
        coEvery { getNamesByTypeForGames(any(), any()) } returns emptyList()
    }
    private val platformRepository = mockk<PlatformRepository>(relaxed = true) {
        every { observeAllPlatforms() } returns flowOf(
            listOf(PlatformEntity(id = 7, slug = "snes", name = "Super Nintendo", shortName = "SNES", romExtensions = "sfc"))
        )
    }
    private val filterMemory = LibraryFilterMemory()

    private fun dependencies() = GameSetBrowser.Dependencies(
        gameRepository = gameRepository,
        collectionRepository = collectionRepository,
        platformRepository = platformRepository,
        imageCacheManager = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true) {
            every { userPreferences } returns flowOf(UserPreferences())
        },
        soundManager = mockk(relaxed = true),
        filterMemory = filterMemory,
        modalResetSignal = com.nendo.argosy.ui.ModalResetSignal()
    )

    @Test
    fun `a collection filters its own games by genre`() = runTest {
        val browser = dependencies().create(backgroundScope, FilterMemoryScope.collection(5))
        val source = flowOf(entities)

        val unfiltered = browser.listing(source).first()
        val genres = browser.filter.value.panel.filterOptions.genres
        browser.setFilterCategory(FilterCategory.GENRE)
        browser.selectFilterOption(genres.indexOf("Action"))
        val filtered = browser.listing(source).first()

        assertEquals(listOf("Action", "RPG"), genres)
        assertEquals(4, unfiltered.listing.games.size)
        assertEquals(listOf(2L), filtered.listing.games.map { it.id })
        assertEquals(4, filtered.totalCount)
    }

    @Test
    fun `hidden games leave a collection unless the hidden source is chosen`() = runTest {
        every { gameRepository.observeHiddenList() } returns flowOf(
            listOf(entity(4, "Hidden Gem").toListItem(isHidden = true))
        )
        val browser = dependencies().create(backgroundScope, FilterMemoryScope.collection(6))

        val listing = browser.listing(flowOf(entities)).first()

        assertEquals(listOf(2L, 1L, 3L), listing.listing.games.map { it.id })
        assertEquals(1, browser.filter.value.panel.hiddenGameCount)
    }

    @Test
    fun `a collection's filters are remembered under its own key and never as the library's`() = runTest {
        val browser = dependencies().create(backgroundScope, FilterMemoryScope.collection(5))
        browser.listing(flowOf(entities)).first()

        browser.setFilterCategory(FilterCategory.GENRE)
        browser.selectFilterOption(0)

        assertNull(filterMemory.filters)
        assertEquals(setOf("Action"), filterMemory.filtersFor(FilterMemoryScope.collection(5))?.genres)
        assertNull(filterMemory.filtersFor(FilterMemoryScope.collection(6)))
        assertNull(filterMemory.filtersFor(FilterMemoryScope.category("genres", "5")))
    }

    @Test
    fun `a reopened collection starts on its remembered filters`() = runTest {
        filterMemory.remember(FilterMemoryScope.collection(5), ActiveFilters(genres = setOf("RPG")))

        val browser = dependencies().create(backgroundScope, FilterMemoryScope.collection(5))

        assertEquals(setOf("RPG"), browser.filter.value.panel.activeFilters.genres)
    }

    @Test
    fun `memory scopes for collections, categories and the library never collide`() {
        val library = ActiveFilters(searchQuery = "library")
        filterMemory.filters = library
        filterMemory.remember(FilterMemoryScope.collection(1), ActiveFilters(searchQuery = "collection"))
        filterMemory.remember(FilterMemoryScope.category("genres", "1"), ActiveFilters(searchQuery = "genre"))

        assertEquals(library, filterMemory.filters)
        assertEquals("collection", filterMemory.filtersFor(FilterMemoryScope.collection(1))?.searchQuery)
        assertEquals("genre", filterMemory.filtersFor(FilterMemoryScope.category("genres", "1"))?.searchQuery)
        assertNull(filterMemory.filtersFor(FilterMemoryScope.category("series", "1")))
        assertNotEquals(FilterMemoryScope.collection(1).key, FilterMemoryScope.category("genres", "1").key)
        assertEquals("collection:1", FilterMemoryScope.collection(1).key)
        assertEquals("category:genres:1", FilterMemoryScope.category("genres", "1").key)
    }

    private fun entity(id: Long, title: String, genre: String? = null) = GameEntity(
        id = id,
        platformId = 7,
        platformSlug = "snes",
        title = title,
        sortTitle = title.lowercase(),
        localPath = null,
        rommId = id,
        igdbId = null,
        source = GameSource.ROMM_REMOTE,
        genre = genre
    )
}
