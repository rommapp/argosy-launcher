package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.LaunchRetryTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchGameUseCaseTest {

    private lateinit var gameLauncher: GameLauncher
    private lateinit var playSessionTracker: PlaySessionTracker
    private lateinit var launchRetryTracker: LaunchRetryTracker
    private lateinit var useCase: LaunchGameUseCase

    @Before
    fun setup() {
        gameLauncher = mockk(relaxed = true)
        playSessionTracker = mockk(relaxed = true)
        launchRetryTracker = mockk(relaxed = true)
        useCase = LaunchGameUseCase(gameLauncher, playSessionTracker, launchRetryTracker)
    }

    @Test
    fun `invoke starts play session on success`() = runTest {
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { `package` } returns "com.emulator.test"
        }
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.Success(intent)

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Success)
        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = "com.emulator.test"
            )
        }
    }

    @Test
    fun `invoke handles null package in intent`() = runTest {
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { `package` } returns null
        }
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.Success(intent)

        useCase(123L)

        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = ""
            )
        }
    }

    @Test
    fun `invoke returns NoEmulator when no emulator available`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.NoEmulator("nes")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.NoEmulator)
        assertEquals("nes", (result as LaunchResult.NoEmulator).platformId)
    }

    @Test
    fun `invoke does not start session on NoEmulator`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.NoEmulator("nes")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns NoRomFile when file missing`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.NoRomFile("/path/to/game.nes")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.NoRomFile)
        assertEquals("/path/to/game.nes", (result as LaunchResult.NoRomFile).gamePath)
    }

    @Test
    fun `invoke does not start session on NoRomFile`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.NoRomFile("/path")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns Error on failure`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.Error("Launch failed")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Error)
        assertEquals("Launch failed", (result as LaunchResult.Error).message)
    }

    @Test
    fun `invoke does not start session on Error`() = runTest {
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.Error("Launch failed")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns success intent`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L) } returns LaunchResult.Success(intent)

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Success)
        assertEquals(intent, (result as LaunchResult.Success).intent)
    }
}
