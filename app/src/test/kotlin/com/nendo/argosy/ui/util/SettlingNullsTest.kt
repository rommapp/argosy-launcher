package com.nendo.argosy.ui.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettlingNullsTest {
    private val upstream = MutableSharedFlow<Long?>(extraBufferCapacity = 16)

    private fun TestScope.collect(): List<Long?> {
        val out = mutableListOf<Long?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            upstream.settlingNulls(SETTLE).collect { out += it }
        }
        runCurrent()
        return out
    }

    private suspend fun TestScope.send(value: Long?) {
        upstream.emit(value)
        runCurrent()
    }

    @Test
    fun `a value that returns inside the window hides the null and is not repeated`() = runTest {
        val out = collect()
        send(1)

        send(null)
        advanceTimeBy(SETTLE - 1)
        send(1)
        advanceTimeBy(SETTLE * 2)

        assertEquals(listOf<Long?>(1), out)
    }

    @Test
    fun `a different value inside the window replaces the null`() = runTest {
        val out = collect()
        send(1)

        send(null)
        advanceTimeBy(SETTLE - 1)
        send(2)
        advanceTimeBy(SETTLE * 2)

        assertEquals(listOf<Long?>(1, 2), out)
    }

    @Test
    fun `a null that stays reaches the collector once the window passes`() = runTest {
        val out = collect()
        send(1)

        send(null)
        advanceTimeBy(SETTLE - 1)
        runCurrent()
        assertEquals(listOf<Long?>(1), out)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf<Long?>(1, null), out)
    }

    @Test
    fun `a null with no value before it passes at once`() = runTest {
        val out = collect()

        send(null)

        assertEquals(listOf<Long?>(null), out)
    }

    @Test
    fun `values pass at once and repeats are dropped`() = runTest {
        val out = collect()

        send(1)
        send(1)
        send(2)

        assertEquals(listOf<Long?>(1, 2), out)
    }

    private companion object {
        const val SETTLE = 150L
    }
}
