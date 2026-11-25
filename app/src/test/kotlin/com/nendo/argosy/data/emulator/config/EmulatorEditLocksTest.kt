package com.nendo.argosy.data.emulator.config

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorEditLocksTest {

    @Test
    fun `a launch waits for an edit of the same package to finish`() = runTest {
        val locks = EmulatorEditLocks()
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()

        launch { locks.withEdit("pkg") { order += "edit start"; release.await(); order += "edit end" } }
        runCurrent()
        val waiting = async { locks.awaitEdits("pkg").also { order += "launch" } }
        runCurrent()

        assertEquals(listOf("edit start"), order)
        release.complete(Unit)
        assertTrue(waiting.await())
        assertEquals(listOf("edit start", "edit end", "launch"), order)
    }

    @Test
    fun `an edit of another package does not hold a launch`() = runTest {
        val locks = EmulatorEditLocks()
        val release = CompletableDeferred<Unit>()

        launch { locks.withEdit("other") { release.await() } }
        runCurrent()

        assertTrue(locks.awaitEdits("pkg"))
        release.complete(Unit)
    }

    @Test
    fun `a second edit of the same package runs after the first`() = runTest {
        val locks = EmulatorEditLocks()
        val order = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()

        launch { locks.withEdit("pkg") { order += "first"; release.await(); order += "first done" } }
        launch { locks.withEdit("pkg") { order += "second" } }
        runCurrent()

        assertEquals(listOf("first"), order)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf("first", "first done", "second"), order)
    }

    @Test
    fun `a launch gives up waiting after the timeout`() = runTest {
        val locks = EmulatorEditLocks()
        val release = CompletableDeferred<Unit>()

        launch { locks.withEdit("pkg") { release.await() } }
        runCurrent()
        val waiting = async { locks.awaitEdits("pkg", timeoutMs = 1_000L) }
        advanceTimeBy(1_001L)

        assertFalse(waiting.await())
        release.complete(Unit)
    }
}
