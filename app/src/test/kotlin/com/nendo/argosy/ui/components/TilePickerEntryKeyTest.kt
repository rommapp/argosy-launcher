package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.HomeTileTargetRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TilePickerEntryKeyTest {

    @Test
    fun `two collections sharing a name get different keys`() {
        val first = TilePickerEntry(HomeTileTargetRef.Collection(1L), "Final Fantasy", "")
        val second = TilePickerEntry(HomeTileTargetRef.Collection(2L), "Final Fantasy", "")

        assertNotEquals(first.key, second.key)
    }

    @Test
    fun `media libraries sharing a name get different keys`() {
        val first = library("lib-a")
        val second = library("lib-b")

        assertNotEquals(first.key, second.key)
    }

    @Test
    fun `the same target keys the same on every rebuild`() {
        val first = TilePickerEntry(HomeTileTargetRef.Collection(1L), "Final Fantasy", "")
        val rebuilt = TilePickerEntry(HomeTileTargetRef.Collection(1L), "Final Fantasy", "12 games")

        assertEquals(first.key, rebuilt.key)
    }

    private fun library(id: String) = TilePickerEntry(
        target = HomeTileTargetRef.Unresolvable,
        title = "Shows",
        subtitle = "",
        action = TilePickerAction.OPEN_MEDIA_LIBRARY,
        libraryId = id
    )
}
