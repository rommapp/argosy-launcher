package com.nendo.argosy.libretro

import android.content.Context
import android.content.pm.ApplicationInfo
import com.nendo.argosy.data.remote.github.GitHubAsset
import com.nendo.argosy.data.remote.github.GitHubRelease
import com.nendo.argosy.data.remote.github.GitHubReleaseClient
import com.nendo.argosy.data.remote.github.ReleaseLookup
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CoreSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val armgba = LibretroCoreRegistry.getCoreById("armgba")!!
    private val source = armgba.source as CoreSource.GitHubRelease
    private val releaseClient: GitHubReleaseClient = mockk()

    private val publishedNames = listOf(
        "armGBA-1.5-aarch64.AppImage",
        "armGBA-1.5-aarch64.zip",
        "armGBA-1.5-android-arm64-v8a.zip",
        "armGBA-1.5-android-arm64.apk",
        "armGBA-1.5-android-armeabi-v7a.zip",
        "armGBA-1.5-android-x86_64.zip",
        "armGBA-1.5-armv7.zip",
        "armGBA-1.5-shaders.zip",
        "armGBA-1.5-x86_64.zip",
        "SHA256SUMS"
    )

    private fun release(names: List<String> = publishedNames) = GitHubRelease(
        tagName = "v1.5",
        name = "armGBA 1.5",
        body = null,
        prerelease = false,
        draft = false,
        htmlUrl = "https://github.com/MagnominusGit/armGBA/releases/tag/v1.5",
        targetCommitish = null,
        assets = names.map { name ->
            GitHubAsset(
                name = name,
                downloadUrl = "https://github.com/MagnominusGit/armGBA/releases/download/v1.5/$name",
                size = 1L,
                digest = "sha256:" + "ab".repeat(32)
            )
        }
    )

    private fun manager(): LibretroCoreManager {
        val context: Context = mockk(relaxed = true) {
            every { filesDir } returns folder.root
            every { applicationInfo } returns ApplicationInfo().apply { nativeLibraryDir = folder.root.path }
        }
        return LibretroCoreManager(
            context = context,
            coreVersionDao = mockk(relaxed = true),
            coreVersionHistoryDao = mockk(relaxed = true),
            coreSystemDataManager = mockk(relaxed = true),
            compatCoreCache = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            releaseClient = releaseClient
        )
    }

    @Test
    fun `armGBA comes from its author's GitHub releases for every Game Boy platform`() {
        assertEquals(CoreSource.GitHubRelease("MagnominusGit", "armGBA", "-android-"), armgba.source)
        assertEquals("armgba_libretro_android.so", armgba.fileName)
        assertEquals(setOf("gb", "gbc", "gba"), armgba.platforms)
        assertFalse(armgba.isDefault)
    }

    @Test
    fun `each ABI matches only its own Android zip`() {
        fun matches(abi: String) = publishedNames.filter { source.assetNameMatches(it, abi) }

        assertEquals(listOf("armGBA-1.5-android-arm64-v8a.zip"), matches("arm64-v8a"))
        assertEquals(listOf("armGBA-1.5-android-armeabi-v7a.zip"), matches("armeabi-v7a"))
        assertEquals(listOf("armGBA-1.5-android-x86_64.zip"), matches("x86_64"))
        assertEquals(emptyList<String>(), matches("x86"))
    }

    @Test
    fun `a GitHub core downloads its ABI zip at the release tag with the asset digest`() = runBlocking {
        coEvery { releaseClient.latestRelease("MagnominusGit", "armGBA") } returns ReleaseLookup.Found(release())

        val target = manager().resolveDownload(armgba, abi = "arm64-v8a")

        assertEquals(
            "https://github.com/MagnominusGit/armGBA/releases/download/v1.5/armGBA-1.5-android-arm64-v8a.zip",
            target.url
        )
        assertEquals("v1.5", target.version)
        assertEquals("ab".repeat(32), target.sha256)
    }

    @Test
    fun `a release without this ABI reads as not published`() = runBlocking {
        coEvery { releaseClient.latestRelease(any(), any()) } returns
            ReleaseLookup.Found(release(publishedNames.filterNot { it.contains("armeabi") }))

        try {
            manager().resolveDownload(armgba, abi = "armeabi-v7a")
            fail("expected a not-published failure")
        } catch (e: CoreDownloadException) {
            assertEquals(CoreDownloadFailure.NotPublished, e.failure)
        }
    }

    @Test
    fun `an unanswered release lookup is a server error and leaves no version`() = runBlocking {
        coEvery { releaseClient.latestRelease(any(), any()) } returns ReleaseLookup.RateLimited(null)

        try {
            manager().resolveDownload(armgba)
            fail("expected a server failure")
        } catch (e: CoreDownloadException) {
            assertEquals(CoreDownloadFailure.ServerError, e.failure)
        }
        assertNull(manager().latestVersion(armgba))
    }

    @Test
    fun `the release tag is the version the update check compares`() = runBlocking {
        coEvery { releaseClient.latestRelease(any(), any()) } returns ReleaseLookup.Found(release())

        assertEquals("v1.5", manager().latestVersion(armgba))
        assertTrue(LibretroCoreManager.isUpdateAvailable("v1.4", "v1.5", blockedVersion = null))
    }

    @Test
    fun `buildbot cores keep their nightly zip url and read the version from the download`() = runBlocking {
        val mgba = LibretroCoreRegistry.getCoreById("mgba")!!

        val target = manager().resolveDownload(mgba, abi = "armeabi-v7a")

        assertEquals(CoreSource.Buildbot, mgba.source)
        assertEquals(
            "https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/mgba_libretro_android.so.zip",
            target.url
        )
        assertNull(target.version)
        assertNull(target.sha256)
    }
}
