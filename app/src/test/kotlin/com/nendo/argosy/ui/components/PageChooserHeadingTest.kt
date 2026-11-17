package com.nendo.argosy.ui.components

import com.nendo.argosy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageChooserHeadingTest {

    @Test
    fun `the list of games keeps the picture chooser's heading`() {
        val browsing = PageChooserState(kind = PageChooserKind.DECOR, page = 0, gameTitle = "")

        assertEquals(R.string.ui_page_chooser_title_decor, browsing.titleRes)
    }

    @Test
    fun `the list of games keeps the backdrop chooser's heading`() {
        val browsing = PageChooserState(kind = PageChooserKind.BACKDROP, page = 0, gameTitle = "")

        assertEquals(R.string.ui_page_chooser_title_backdrop, browsing.titleRes)
    }

    @Test
    fun `an opened game is headed by its own title`() {
        val opened = PageChooserState(kind = PageChooserKind.DECOR, page = 0, gameId = 4L, gameTitle = "Ico")

        assertNull(opened.titleRes)
    }
}
