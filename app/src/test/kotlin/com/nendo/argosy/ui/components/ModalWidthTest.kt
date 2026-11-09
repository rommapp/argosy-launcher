package com.nendo.argosy.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ModalWidthTest {

    @Test
    fun `a pane narrower than the minimum width gets a modal that fits inside it`() {
        assertEquals(248.4.dp.value, modalWidth(400.dp, 276.dp).value, 0.01f)
    }

    @Test
    fun `a wide pane keeps the scaled width`() {
        assertEquals(400.dp, modalWidth(400.dp, 1280.dp))
    }

    @Test
    fun `a small scaled width is raised to the minimum when the pane has room`() {
        assertEquals(280.dp, modalWidth(200.dp, 1280.dp))
    }

    @Test
    fun `a scaled width wider than the pane is capped at nine tenths of it`() {
        assertEquals(450.dp.value, modalWidth(800.dp, 500.dp).value, 0.01f)
    }
}
