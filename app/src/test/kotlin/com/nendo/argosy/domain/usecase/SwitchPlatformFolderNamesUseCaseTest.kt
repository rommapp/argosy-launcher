package com.nendo.argosy.domain.usecase

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.GamePathRewriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchPlatformFolderNamesUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var base: File
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var useCase: SwitchPlatformFolderNamesUseCase
    private val platforms = mutableListOf<PlatformEntity>()
    private val games = mutableListOf<GameEntity>()
    private val rewrites = mutableListOf<Pair<String, String>>()

    @Before
    fun setup() {
        base = tempFolder.newFolder("roms")
        preferences = mockk(relaxed = true)
        val resolver = mockk<PlatformFolderResolver> { coEvery { storageBase() } returns base }
        val platformDao = mockk<PlatformDao> { coEvery { getAllPlatforms() } answers { platforms.toList() } }
        val gameRepository = mockk<GameRepository> {
            coEvery { getGamesWithLocalPathsForPlatform(any()) } answers {
                games.filter { it.platformId == firstArg<Long>() }
            }
        }
        val pathRewriter = mockk<GamePathRewriter>()
        val captured = slot<List<Pair<String, String>>>()
        coEvery { pathRewriter.rewrite(capture(captured)) } answers { rewrites += captured.captured }
        useCase = SwitchPlatformFolderNamesUseCase(
            resolver,
            platformDao,
            gameRepository,
            mockk(relaxed = true),
            mockk(relaxed = true),
            pathRewriter,
            preferences,
            mockk(relaxed = true)
        )
    }

    private fun platform(id: Long, slug: String, fsSlug: String?) =
        PlatformEntity(id = id, slug = slug, fsSlug = fsSlug, name = slug, shortName = slug, romExtensions = "")
            .also { platforms += it }

    private fun rom(relative: String, platformId: Long, slug: String): File {
        val file = File(base, relative).apply { parentFile?.mkdirs(); writeText(relative) }
        games += GameEntity(
            id = games.size + 1L,
            platformId = platformId,
            platformSlug = slug,
            title = relative,
            sortTitle = relative,
            localPath = file.absolutePath,
            rommId = null,
            igdbId = null,
            source = GameSource.LOCAL_ONLY
        )
        return file
    }

    @Test
    fun `a platform alone in its folder has the whole folder renamed`() = runTest {
        platform(1, "psx", "ps")
        rom("psx/Game/game.chd", 1, "psx")
        File(base, "psx/extcontent").mkdirs()

        val result = useCase(useFsSlug = true)

        assertEquals(FolderNameSwitchResult.Switched(1), result)
        assertFalse(File(base, "psx").exists())
        assertTrue(File(base, "ps/Game/game.chd").exists())
        assertTrue(File(base, "ps/extcontent").isDirectory)
        assertEquals(listOf(File(base, "psx").absolutePath to File(base, "ps").absolutePath), rewrites)
        coVerify { preferences.setPlatformFoldersFromServer(true) }
    }

    @Test
    fun `turning it off renames the folder back`() = runTest {
        platform(1, "psx", "ps")
        rom("ps/game.chd", 1, "psx")

        useCase(useFsSlug = false)

        assertTrue(File(base, "psx/game.chd").exists())
        assertFalse(File(base, "ps").exists())
        coVerify { preferences.setPlatformFoldersFromServer(false) }
    }

    @Test
    fun `a version sharing its slug folder moves only its own games`() = runTest {
        platform(1, "gba", "gba")
        platform(2, "gba", "gba-hacks")
        rom("gba/retail.gba", 1, "gba")
        rom("gba/hack.gba", 2, "gba")

        val result = useCase(useFsSlug = true)

        assertEquals(FolderNameSwitchResult.Switched(1), result)
        assertTrue(File(base, "gba/retail.gba").exists())
        assertTrue(File(base, "gba-hacks/hack.gba").exists())
        assertFalse(File(base, "gba/hack.gba").exists())
    }

    @Test
    fun `an fs slug another platform owns keeps the slug folder`() = runTest {
        platform(1, "famicom", "nes")
        platform(2, "nes", null)
        rom("famicom/game.nes", 1, "famicom")

        assertEquals(0, useCase.pendingMoves(useFsSlug = true))
        useCase(useFsSlug = true)

        assertTrue(File(base, "famicom/game.nes").exists())
    }

    @Test
    fun `a name clash undoes earlier moves and keeps the setting`() = runTest {
        platform(1, "gba", "gba")
        platform(2, "gba", "gba-hacks")
        rom("gba/a.gba", 2, "gba")
        rom("gba/b.gba", 2, "gba")
        File(base, "gba-hacks").mkdirs()
        File(base, "gba-hacks/b.gba").writeText("someone else's")

        val result = useCase(useFsSlug = true)

        assertTrue(result is FolderNameSwitchResult.Failed)
        assertTrue(File(base, "gba/a.gba").exists())
        assertTrue(File(base, "gba/b.gba").exists())
        assertFalse(File(base, "gba-hacks/a.gba").exists())
        assertEquals("someone else's", File(base, "gba-hacks/b.gba").readText())
        coVerify(exactly = 0) { preferences.setPlatformFoldersFromServer(any()) }
    }
}
