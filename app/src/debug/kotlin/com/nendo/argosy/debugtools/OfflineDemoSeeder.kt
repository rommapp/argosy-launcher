package com.nendo.argosy.debugtools

import android.content.Context
import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import java.io.File

internal suspend fun seedOfflineDemo(context: Context, database: ALauncherDatabase): Int {
    val directory = File(requireNotNull(context.getExternalFilesDir(null)), "ui-demo")
    require(directory.isDirectory || directory.mkdirs()) {
        "Could not create app-owned demo directory: ${directory.absolutePath}"
    }
    val scenarios = context.assets.open("ui-demo-scenarios.json").bufferedReader().use { it.readText() }
    val fixtures = readOfflineDemoFixtures(directory, scenarios)
    database.withTransaction {
        val platformDao = database.platformDao()
        val gameDao = database.gameDao()
        val artDao = database.gameArtDao()
        val platform = platformDao.getById(DEMO_PLATFORM_ID)
        require(platform == null || (platform.slug == "nes" && platform.fsSlug == "argosy-ui-demo")) {
            "Demo platform ID belongs to another platform; nothing imported"
        }
        platformDao.insert(
            PlatformEntity(
                id = DEMO_PLATFORM_ID,
                slug = "nes",
                fsSlug = "argosy-ui-demo",
                name = "Nintendo Entertainment System",
                shortName = "NES",
                romExtensions = "nes"
            )
        )
        fixtures.forEach { fixture ->
            val existing = gameDao.getById(fixture.game.id)
            require(existing == null || (
                existing.platformId == DEMO_PLATFORM_ID && existing.source == GameSource.LOCAL_ONLY &&
                    existing.title == fixture.game.title && existing.localPath == fixture.game.localPath && existing.rommId == null
                )) { "Demo game ID collision: ${fixture.game.id}; nothing imported" }
            if (existing == null) gameDao.insert(fixture.game)
            for (slot in listOf(ArtSlot.COVER, ArtSlot.BACKGROUND)) {
                if (artDao.get(fixture.game.id, slot.name) == null) {
                    artDao.setCached(fixture.game.id, slot, fixture.artwork.absolutePath, null)
                    if (slot == ArtSlot.COVER) artDao.setCoverAspectRatio(fixture.game.id, fixture.aspectRatio)
                }
            }
        }
        platformDao.updateGameCount(DEMO_PLATFORM_ID, gameDao.countByPlatform(DEMO_PLATFORM_ID, null))
    }
    return fixtures.size
}
