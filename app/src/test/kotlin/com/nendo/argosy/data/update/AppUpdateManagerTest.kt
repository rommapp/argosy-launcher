package com.nendo.argosy.data.update

import android.content.Context
import com.nendo.argosy.data.model.AppUpdateDownload
import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.data.preferences.AppPreferences
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val installer: AppInstaller = mockk(relaxed = true)
    private val bytes = ByteArray(64 * 1024) { it.toByte() }

    private lateinit var filesDir: File
    private lateinit var manager: AppUpdateManager
    private lateinit var release: AppUpdateRelease

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.start()
        filesDir = folder.newFolder("files")
        val context: Context = mockk(relaxed = true) {
            every { filesDir } returns this@AppUpdateManagerTest.filesDir
            every { cacheDir } returns folder.newFolder("cache")
            every { applicationContext } returns this
        }
        val prefs: AppPreferencesRepository = mockk {
            every { preferences } returns flowOf(mockk<AppPreferences>(relaxed = true))
        }
        manager = AppUpdateManager(context, mockk(relaxed = true), prefs, installer)
        release = AppUpdateRelease(
            tag = "v9.0.0",
            name = "v9.0.0",
            notes = null,
            assetName = "argosy-v9.0.0-arm64.apk",
            assetUrl = server.url("/argosy.apk").toString(),
            assetSize = bytes.size.toLong(),
            assetDigest = null,
            abi = "arm64"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun slowBody() = MockResponse()
        .setBody(Buffer().write(bytes))
        .throttleBody(4 * 1024, 100, TimeUnit.MILLISECONDS)

    private fun awaitDownload(predicate: (AppUpdateDownload) -> Boolean) = runBlocking {
        withTimeout(TIMEOUT_MS) { manager.download.first(predicate) }
    }

    @Test
    fun `a finished download goes to the installer`() {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))

        manager.install(release)
        awaitDownload { it is AppUpdateDownload.HandedOff }

        verify(exactly = 1) { installer.installApk(any(), any()) }
    }

    @Test
    fun `skip during a download cancels it and never opens the installer`() {
        server.enqueue(slowBody())

        manager.install(release)
        awaitDownload { it is AppUpdateDownload.Downloading && it.progress > 0 }
        manager.skip(release.tag)
        Thread.sleep(SETTLE_MS)

        verify(exactly = 0) { installer.installApk(any(), any()) }
        assertEquals(AppUpdateDownload.Idle, manager.download.value)
        assertFalse(UpdateApkFiles(File(filesDir, "updates")).partFor(release).exists())
    }

    @Test
    fun `later during a download cancels it and never opens the installer`() {
        server.enqueue(slowBody())

        manager.install(release)
        awaitDownload { it is AppUpdateDownload.Downloading && it.progress > 0 }
        manager.later(release.tag)
        Thread.sleep(SETTLE_MS)

        verify(exactly = 0) { installer.installApk(any(), any()) }
        assertFalse(UpdateApkFiles(File(filesDir, "updates")).isStored(release))
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 2_500L
    }
}
