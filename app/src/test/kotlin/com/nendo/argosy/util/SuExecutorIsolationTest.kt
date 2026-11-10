package com.nendo.argosy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SuExecutorIsolationTest {

    private val name = SuExecutor::class.simpleName!!
    private val reference = Regex("\\b$name\\b")

    private fun mainSources(): File {
        val root = listOf(File("src/main/kotlin"), File("app/src/main/kotlin")).firstOrNull { it.isDirectory }
        assertTrue("Could not locate the main source tree", root != null)
        return root!!
    }

    @Test
    fun `root shell never consults the su route`() {
        val rootShell = File(mainSources(), "com/nendo/argosy/util/RootShell.kt").readText()

        assertFalse(reference.containsMatchIn(rootShell))
    }

    @Test
    fun `only the performance resolver and the user-approved privileged access route reach su`() {
        val users = mainSources().walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SuExecutor.kt" }
            .filter { reference.containsMatchIn(it.readText()) }
            .map { it.name }
            .toSet()

        assertEquals(setOf("DevicePerformance.kt", "PrivilegedAccess.kt"), users)
    }
}
