package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class CrashRecorderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val crash = IllegalStateException("pad 7 has no profile at http://192.168.1.20:8080/api")

    @Test
    fun `a crash is written with its thread, process and stack trace`() {
        val directory = folder.newFolder("crashes")

        val file = CrashRecorder.record(
            directory, "3.0.0-beta.9", "com.nendo.argosy:slot", "main", crash, LocalDateTime.of(2026, 11, 20, 4, 52, 1)
        )

        assertEquals("3.0.0-beta.9-2026-11-20_04-52-01-com.nendo.argosy_slot.txt", file.name)
        val text = file.readText()
        assertTrue(text, "thread = main" in text)
        assertTrue(text, "process = com.nendo.argosy:slot" in text)
        assertTrue(text, "java.lang.IllegalStateException: pad 7 has no profile" in text)
        assertTrue(text, "CrashRecorderTest" in text)
    }

    @Test
    fun `the recorded stack trace goes through the log sanitizer`() {
        val directory = folder.newFolder("crashes")

        val text = CrashRecorder.record(directory, "v", "main", "main", crash, LocalDateTime.now()).readText()

        assertFalse(text, "192.168.1.20" in text)
    }

    @Test
    fun `only the newest five crashes are kept`() {
        val directory = folder.newFolder("crashes")
        val start = LocalDateTime.of(2026, 11, 20, 4, 0, 0)

        val written = (0 until 7).map { minute ->
            CrashRecorder.record(directory, "v", "main", "main", crash, start.plusMinutes(minute.toLong()))
                .also { it.setLastModified(1_000_000L + minute * 60_000L) }
        }
        CrashRecorder.record(directory, "v", "main", "main", crash, start.plusMinutes(10))

        val kept = CrashRecorder.filesIn(directory).map { it.name }
        assertEquals(5, kept.size)
        assertFalse(written[0].name in kept)
        assertFalse(written[1].name in kept)
        assertFalse(written[2].name in kept)
    }
}
