package com.nendo.argosy.data.emulator.config

import android.content.Context
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.emulator.background.RunningApp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

class EmulatorConfigWriterGameGuardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pkg = "org.dolphinemu.dolphinemu"
    private val option = DolphinConfigSpec.SPEC.options.first { it.key == "VSync" }

    private fun writer(
        active: ActiveSession? = null,
        background: RunningApp? = null
    ): Pair<EmulatorConfigWriter, EmulatorDataAccess> {
        val context = mockk<Context> {
            every { filesDir } returns temp.newFolder()
            every { cacheDir } returns temp.newFolder()
        }
        val access = mockk<EmulatorDataAccess>()
        val sessions = mockk<PlaySessionTracker> {
            every { activeSession } returns MutableStateFlow(active)
        }
        val backgroundGames = mockk<BackgroundGames> {
            coEvery { liveApp(pkg) } returns background
        }
        val writer = EmulatorConfigWriter(context, access, mockk(), EmulatorEditLocks(), { sessions }, { backgroundGames })
        return writer to access
    }

    private fun session(emulatorPackage: String) =
        ActiveSession(gameId = 1L, startTime = Instant.ofEpochSecond(1), emulatorPackage = emulatorPackage)

    @Test
    fun `a game being played refuses edits and undo before any file is touched`() = runBlocking {
        val (writer, access) = writer(active = session(pkg))

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING), writer.write("dolphin", pkg, option, "True"))
        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING), writer.undo("dolphin", pkg, option))
        coVerify(exactly = 0) { access.capabilities(any(), any()) }
    }

    @Test
    fun `a game left running in the background holds the emulator`() = runBlocking {
        val app = mockk<RunningApp> { every { game } returns mockk() }

        assertEquals(true, writer(background = app).first.holdsGame(pkg))
    }

    @Test
    fun `another emulator's game or an emulator open without a game does not hold it`() = runBlocking {
        val idle = mockk<RunningApp> { every { game } returns null }

        assertFalse(writer(active = session("info.cemu.cemu")).first.holdsGame(pkg))
        assertFalse(writer(background = idle).first.holdsGame(pkg))
    }
}
