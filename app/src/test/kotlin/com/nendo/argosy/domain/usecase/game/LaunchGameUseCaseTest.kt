package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.permissions.PermissionWatch
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.data.sync.CrossEmulatorSaveMigrator
import com.nendo.argosy.data.wallpaper.LockScreenArtManager
import com.nendo.argosy.domain.model.LaunchStep
import io.mockk.coVerifyOrder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LaunchGameUseCaseTest {

    private lateinit var gameLauncher: GameLauncher
    private lateinit var playSessionTracker: PlaySessionTracker
    private lateinit var libraryPointerRepair: LibraryPointerRepair
    private lateinit var lockScreenArtManager: LockScreenArtManager
    private lateinit var tracker: LaunchProgressTracker
    private lateinit var migrator: CrossEmulatorSaveMigrator
    private lateinit var permissionWatch: PermissionWatch
    private lateinit var useCase: LaunchGameUseCase

    @Before
    fun setup() {
        gameLauncher = mockk(relaxed = true)
        playSessionTracker = mockk(relaxed = true)
        libraryPointerRepair = mockk(relaxed = true)
        lockScreenArtManager = mockk(relaxed = true)
        migrator = mockk(relaxed = true)
        coEvery { migrator.migrateBeforeLaunch(any()) } returns CrossEmulatorSaveMigrator.Outcome.Untouched
        permissionWatch = mockk(relaxed = true)
        coEvery { permissionWatch.clearForLaunch() } returns true
        tracker = LaunchProgressTracker(CoroutineScope(UnconfinedTestDispatcher()))
        useCase = LaunchGameUseCase(
            gameLauncher,
            playSessionTracker,
            libraryPointerRepair,
            lockScreenArtManager,
            tracker,
            mockk(relaxed = true),
            migrator,
            permissionWatch
        )
    }

    @Test
    fun `a fresh launch the permission sheet closes resolves nothing`() = runTest {
        coEvery { permissionWatch.clearForLaunch() } returns false

        val result = useCase(123L)

        assertEquals(LaunchResult.Cancelled, result)
        coVerify(exactly = 0) { migrator.migrateBeforeLaunch(any()) }
        coVerify(exactly = 0) { gameLauncher.launch(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a resume skips the permission check`() = runTest {
        coEvery { gameLauncher.launch(123L, null, true, any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L, forResume = true)

        coVerify(exactly = 0) { permissionWatch.clearForLaunch() }
    }

    @Test
    fun `a fresh launch carries the save across emulators before resolving the launch`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L)

        coVerifyOrder {
            migrator.migrateBeforeLaunch(123L)
            gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a resume and a launch whose caller already prepared the saves skip the migration`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L, forResume = true)
        useCase(123L, savesPrepared = true)

        coVerify(exactly = 0) { migrator.migrateBeforeLaunch(any()) }
    }

    @Test
    fun `a launch cancelled while preparing the UI never opens and puts the library art back`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)
        coEvery { lockScreenArtManager.showBeforeLaunch(123L) } answers { tracker.cancel() }

        val result = useCase(123L)

        assertEquals(LaunchResult.Cancelled, result)
        verify { lockScreenArtManager.showLibraryAfterCancelledLaunch() }
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch cancelled while its intent resolves never reaches the lock screen art`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } answers {
            tracker.cancel()
            LaunchResult.Success(intent)
        }

        val result = useCase(123L)

        assertEquals(LaunchResult.Cancelled, result)
        coVerify(exactly = 0) { lockScreenArtManager.showBeforeLaunch(any()) }
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a launch the use case opened ends on its game screen`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        useCase(123L)

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
    }

    @Test
    fun `a launch the caller opened stays open for the caller to finish`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)
        val ticket = tracker.begin(123L, "Game")!!

        useCase(123L)

        assertEquals(ticket, tracker.current)
    }

    @Test
    fun `an in-process launch shows its lock screen art before returning the intent`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns
            LaunchResult.Success(intent, inProcess = true)

        useCase(123L)

        coVerifyOrder {
            gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any())
            lockScreenArtManager.showBeforeLaunch(123L)
        }
    }

    @Test
    fun `a resume leaves the lock screen art alone`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, true, any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        useCase(123L, forResume = true)

        coVerify(exactly = 0) { lockScreenArtManager.showBeforeLaunch(any()) }
    }

    @Test
    fun `a failed launch leaves the lock screen art alone`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L)

        coVerify(exactly = 0) { lockScreenArtManager.showBeforeLaunch(any()) }
    }

    @Test
    fun `invoke starts play session on success`() = runTest {
        val component = mockk<android.content.ComponentName>(relaxed = true) {
            coEvery { packageName } returns "com.emulator.test"
        }
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { this@mockk.component } returns component
        }
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Success)
        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = "com.emulator.test",
                coreName = null,
                isNewGame = true
            )
        }
    }

    @Test
    fun `an external launch whose session starts at its window returns that session and starts none`() = runTest {
        val component = mockk<android.content.ComponentName>(relaxed = true) {
            coEvery { packageName } returns "com.emulator.test"
        }
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { this@mockk.component } returns component
            coEvery { getStringExtra("LIBRETRO") } returns "/data/cores/citra_libretro_android.so"
        }
        coEvery { gameLauncher.launch(123L, any(), any(), any(), 50L, any(), any(), any()) } returns
            LaunchResult.Success(intent, variantFileId = 50L)

        val result = useCase(123L, variantFileId = 50L, origin = LaunchOrigin.EXTERNAL, sessionAtWindow = true)

        assertEquals(
            ExternalSessionStart(123L, "com.emulator.test", "citra", 50L, LaunchOrigin.EXTERNAL),
            (result as LaunchResult.Success).session
        )
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an in-process launch and a resume owe no session at the window`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, false, any(), any(), any(), any(), any()) } returns
            LaunchResult.Success(intent, inProcess = true)
        coEvery { gameLauncher.launch(123L, null, true, any(), any(), any(), any(), any()) } returns
            LaunchResult.Success(intent)

        val inProcess = useCase(123L, sessionAtWindow = true)
        val resumed = useCase(123L, forResume = true, sessionAtWindow = true)

        assertNull((inProcess as LaunchResult.Success).session)
        assertNull((resumed as LaunchResult.Success).session)
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an external launch that starts its own session returns none to start again`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        val result = useCase(123L)

        assertNull((result as LaunchResult.Success).session)
        coVerify(exactly = 1) { playSessionTracker.startSession(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `invoke waits for the library pointer repair before resolving the launch`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L)

        coVerifyOrder {
            libraryPointerRepair.await()
            gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `invoke handles null package in intent`() = runTest {
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { component } returns null
            coEvery { `package` } returns null
        }
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        useCase(123L)

        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = "",
                coreName = null,
                isNewGame = true
            )
        }
    }

    @Test
    fun `invoke carries the launch origin into the session`() = runTest {
        val component = mockk<android.content.ComponentName>(relaxed = true) {
            coEvery { packageName } returns "com.emulator.test"
        }
        val intent = mockk<Intent>(relaxed = true) {
            coEvery { this@mockk.component } returns component
        }
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        useCase(123L, origin = LaunchOrigin.EXTERNAL)

        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = "com.emulator.test",
                coreName = null,
                isNewGame = true,
                origin = LaunchOrigin.EXTERNAL
            )
        }
    }

    @Test
    fun `a requested variant that fell back to the primary rom does not tag the session`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, any(), any(), any(), 50L, any(), any(), any()) } returns
            LaunchResult.Success(intent, variantFileId = null)

        useCase(123L, variantFileId = 50L)

        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = any(),
                coreName = any(),
                isHardcore = any(),
                isNewGame = true,
                isNetplayGuest = any(),
                variantFileId = null,
                origin = any()
            )
        }
    }

    @Test
    fun `a variant that launched tags the session with its file`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, any(), any(), any(), 50L, any(), any(), any()) } returns
            LaunchResult.Success(intent, variantFileId = 50L)

        useCase(123L, variantFileId = 50L)

        coVerify {
            playSessionTracker.startSession(
                gameId = 123L,
                emulatorPackage = any(),
                coreName = any(),
                isHardcore = any(),
                isNewGame = true,
                isNetplayGuest = any(),
                variantFileId = 50L,
                origin = any()
            )
        }
    }

    @Test
    fun `invoke leaves the session to LibretroActivity for an in-process launch`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns
            LaunchResult.Success(intent, inProcess = true)

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Success)
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke keeps the running session on resume`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, true, any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        val result = useCase(123L, forResume = true)

        assertTrue(result is LaunchResult.Success)
        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns NoEmulator when no emulator available`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.NoEmulator)
        assertEquals("nes", (result as LaunchResult.NoEmulator).platformSlug)
    }

    @Test
    fun `invoke does not start session on NoEmulator`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoEmulator("nes")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns NoRomFile when file missing`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoRomFile("/path/to/game.nes")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.NoRomFile)
        assertEquals("/path/to/game.nes", (result as LaunchResult.NoRomFile).gamePath)
    }

    @Test
    fun `invoke does not start session on NoRomFile`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.NoRomFile("/path")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns Error on failure`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Error("Launch failed")

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Error)
        assertEquals("Launch failed", (result as LaunchResult.Error).message)
    }

    @Test
    fun `invoke does not start session on Error`() = runTest {
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Error("Launch failed")

        useCase(123L)

        coVerify(exactly = 0) { playSessionTracker.startSession(any(), any()) }
    }

    @Test
    fun `invoke returns success intent`() = runTest {
        val intent = mockk<Intent>(relaxed = true)
        coEvery { gameLauncher.launch(123L, null, any(), any(), any(), any(), any(), any()) } returns LaunchResult.Success(intent)

        val result = useCase(123L)

        assertTrue(result is LaunchResult.Success)
        assertEquals(intent, (result as LaunchResult.Success).intent)
    }
}
