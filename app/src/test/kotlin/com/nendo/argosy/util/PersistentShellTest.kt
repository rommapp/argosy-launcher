package com.nendo.argosy.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentShellTest {

    private val shell = PersistentShell(listOf("sh"))

    @After
    fun tearDown() {
        shell.stop()
    }

    private fun pidOf(): String = shell.run("echo \$PPID", TIMEOUT)!!.output.single()

    @Test
    fun `every script runs in the one shell process`() {
        val first = pidOf()
        repeat(5) { shell.run("true", TIMEOUT) }

        assertEquals(first, pidOf())
    }

    @Test
    fun `a script's output and exit code come back`() {
        val result = shell.run("echo one\necho two >&2\nexit 3", TIMEOUT)!!

        assertEquals(listOf("one", "two"), result.output)
        assertEquals(3, result.exitCode)
    }

    @Test
    fun `exit in a script leaves the shell running`() {
        val before = pidOf()
        shell.run("exit 1", TIMEOUT)

        assertEquals(before, pidOf())
    }

    @Test
    fun `output without a final newline is kept`() {
        val result = shell.run("printf partial", TIMEOUT)!!

        assertEquals(listOf("partial"), result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `quotes and dollar signs reach the script unexpanded`() {
        val result = shell.run("x='a b'\necho \"\$x\" '\$HOME'", TIMEOUT)!!

        assertEquals(listOf("a b \$HOME"), result.output)
    }

    @Test
    fun `a script past its limit returns null and the next call starts a new shell`() {
        val before = pidOf()

        assertNull(shell.run("sleep 5", 1))
        assertNotEquals(before, pidOf())
    }

    @Test
    fun `a shell that died is replaced`() {
        val before = pidOf()
        shell.run("kill -9 \$PPID", TIMEOUT)

        assertNotEquals(before, pidOf())
    }

    private companion object {
        const val TIMEOUT = 10L
    }
}
