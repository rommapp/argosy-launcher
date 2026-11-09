package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSpansTest {

    private fun spans(vararg transitions: Pair<Long, Boolean>) =
        PermissionHelper.spansFromTransitions(transitions.toList(), fromMs = 0L, toMs = 100L)

    @Test
    fun `a package that never leaves is in front for the whole window`() {
        assertEquals(listOf(0L..100L), spans())
    }

    @Test
    fun `leaving and returning splits the window`() {
        assertEquals(listOf(0L..30L, 60L..100L), spans(30L to false, 60L to true))
    }

    @Test
    fun `a package that leaves for good closes its span`() {
        assertEquals(listOf(0L..40L), spans(40L to false))
    }

    @Test
    fun `repeated foreground events do not reopen a span`() {
        assertEquals(listOf(0L..50L), spans(10L to true, 50L to false))
    }
}
