package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformSetupBackupTest {

    private fun platform(id: Long, slug: String, fsSlug: String? = null) = PlatformEntity(
        id = id, slug = slug, fsSlug = fsSlug, name = slug, shortName = slug, romExtensions = "zip"
    )

    private fun game(id: Long, platformId: Long, rommId: Long?) = mockk<GameEntity> {
        io.mockk.every { this@mockk.id } returns id
        io.mockk.every { this@mockk.platformId } returns platformId
        io.mockk.every { this@mockk.rommId } returns rommId
    }

    private val sourcePsx = platform(3, "psx")
    private val sourceArcade = platform(7, "arcade", fsSlug = "fbneo")

    private fun sourceBackup(): PlatformSetupBackup {
        val configs = mockk<EmulatorConfigDao>()
        val args = mockk<EmulatorLaunchArgsDao>()
        val platforms = mockk<PlatformDao>()
        val games = mockk<GameDao>()
        coEvery { platforms.getAllPlatforms() } returns listOf(sourcePsx, sourceArcade)
        coEvery { configs.getPlatformDefaults() } returns listOf(
            EmulatorConfigEntity(
                platformId = 3, gameId = null, packageName = "com.github.stenzek.duckstation",
                displayName = "DuckStation", coreName = null, isDefault = true, preferredExtension = "chd"
            ),
            EmulatorConfigEntity(
                platformId = 7, gameId = null, packageName = "com.retroarch", displayName = "RetroArch",
                coreName = "fbneo", isDefault = true
            )
        )
        coEvery { configs.getGameOverrides() } returns listOf(
            EmulatorConfigEntity(
                platformId = 3, gameId = 50, packageName = "com.retroarch", displayName = "RetroArch",
                coreName = "swanstation", savePath = "/storage/emulated/0/saves", gpuDriver = "turnip",
                graphicsBackend = "vulkan"
            ),
            EmulatorConfigEntity(
                platformId = 3, gameId = 51, packageName = "com.retroarch", displayName = "RetroArch",
                coreName = "pcsx_rearmed"
            )
        )
        coEvery { games.getByIds(any()) } returns listOf(game(50, 3, rommId = 9001), game(51, 3, rommId = null))
        coEvery { args.getAll() } returns listOf(
            EmulatorLaunchArgsEntity(platformId = 3, emulatorId = "duckstation", dataBinding = "FILE_PROVIDER")
        )
        return PlatformSetupBackup(configs, args, platforms, games)
    }

    private class Target {
        val configs = mockk<EmulatorConfigDao>(relaxed = true)
        val args = mockk<EmulatorLaunchArgsDao>(relaxed = true)
        val platforms = mockk<PlatformDao>()
        val games = mockk<GameDao>()
        val inserted = mutableListOf<EmulatorConfigEntity>()
        val upserted = mutableListOf<EmulatorLaunchArgsEntity>()

        init {
            val config = slot<EmulatorConfigEntity>()
            coEvery { configs.insert(capture(config)) } answers { inserted += config.captured; 1L }
            val launch = slot<EmulatorLaunchArgsEntity>()
            coEvery { args.upsert(capture(launch)) } answers { upserted += launch.captured }
            coEvery { platforms.getBySlugAndFsSlug(any(), any()) } returns null
            coEvery { games.getByRommId(any()) } returns null
        }

        fun backup() = PlatformSetupBackup(configs, args, platforms, games)
    }

    @Test
    fun `platform picks land on the platform with the same slug under its new id`() = runBlocking {
        val section = sourceBackup().export()
        val target = Target()
        coEvery { target.platforms.getBySlugAndFsSlug("psx", null) } returns platform(30, "psx")
        coEvery { target.platforms.getBySlugAndFsSlug("arcade", "fbneo") } returns platform(70, "arcade", "fbneo")

        target.backup().import(section)

        val psx = target.inserted.single { it.platformId == 30L }
        assertEquals("com.github.stenzek.duckstation", psx.packageName)
        assertEquals("chd", psx.preferredExtension)
        assertEquals(true, psx.isDefault)
        assertEquals("fbneo", target.inserted.single { it.platformId == 70L }.coreName)
        coVerify { target.configs.clearPlatformDefaults(30) }
    }

    @Test
    fun `a game pick follows its RomM id and keeps the target's own save path and driver`() = runBlocking {
        val section = sourceBackup().export()
        val target = Target()
        coEvery { target.games.getByRommId(9001) } returns game(500, 30, rommId = 9001)
        coEvery { target.configs.getByGameId(500) } returns EmulatorConfigEntity(
            platformId = 30, gameId = 500, packageName = null, displayName = null, coreName = null,
            savePath = "/target/saves", gpuDriver = "system"
        )

        target.backup().import(section)

        val pick = target.inserted.single { it.gameId == 500L }
        assertEquals("swanstation", pick.coreName)
        assertEquals("vulkan", pick.graphicsBackend)
        assertEquals("/target/saves", pick.savePath)
        assertEquals("system", pick.gpuDriver)
    }

    @Test
    fun `the export never carries file locations or GPU drivers`() = runBlocking {
        val text = sourceBackup().export().toString()

        assertEquals(false, text.contains("/storage/emulated/0/saves"))
        assertEquals(false, text.contains("turnip"))
    }

    @Test
    fun `a game with no RomM id is left out of the export`() = runBlocking {
        val games = sourceBackup().export().getJSONArray("games")

        assertEquals(1, games.length())
        assertEquals(9001L, games.getJSONObject(0).getLong("romm_id"))
    }

    @Test
    fun `rows with no match on this install are counted as skipped`() = runBlocking {
        val section = sourceBackup().export()
        val target = Target()
        coEvery { target.platforms.getBySlugAndFsSlug("psx", null) } returns platform(30, "psx")

        val result = target.backup().import(section)

        assertEquals(2, result.applied)
        assertEquals(2, result.skipped)
        assertEquals("duckstation", target.upserted.single().emulatorId)
        assertEquals(30L, target.upserted.single().platformId)
        assertNull(target.inserted.firstOrNull { it.gameId != null })
    }

    @Test
    fun `an import with no matching platform writes nothing`() = runBlocking {
        val target = Target()

        val result = target.backup().import(JSONObject(sourceBackup().export().toString()))

        assertEquals(0, result.applied)
        assertEquals(4, result.skipped)
        assertEquals(emptyList<EmulatorConfigEntity>(), target.inserted)
    }
}
