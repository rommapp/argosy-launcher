package com.nendo.argosy.debugtools

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

internal const val DEMO_PLATFORM_ID = -87_000_000L
private const val DEMO_GAME_ID_BASE = 87_000_000L
private const val MAX_FIXTURE_BYTES = 16 * 1024 * 1024

internal data class OfflineDemoFixture(
    val game: GameEntity,
    val artwork: File,
    val aspectRatio: Float
)

internal fun readOfflineDemoFixtures(directory: File, scenariosText: String): List<OfflineDemoFixture> {
    val manifestFile = File(directory, "seed-manifest.json").canonicalFile
    require(manifestFile.parentFile == directory.canonicalFile && manifestFile.length() in 1L..MAX_FIXTURE_BYTES.toLong()) {
        "Missing, unsafe or oversized seed manifest"
    }
    val sources = JSONObject(manifestFile.readText()).getJSONArray("games").objects()
    require(sources.size == 12) { "Expected 12 homebrew sources" }
    require(sources.map { it.getString("slug") }.distinct().size == sources.size) { "Duplicate source slug" }
    val verifiedSources = sources.associate { source ->
        val slug = source.getString("slug")
        require(source.getString("platformSlug") == "nes") { "Unsupported fixture platform" }
        val files = source.getJSONArray("files").objects()
        val rom = verifyDemoFile(directory, slug, "nes", files)
        val artwork = verifyDemoFile(directory, slug, "png", files)
        val header = ByteArray(24)
        DataInputStream(artwork.inputStream()).use { it.readFully(header) }
        val dimensions = ByteBuffer.wrap(header, 16, 8)
        val width = dimensions.int
        val height = dimensions.int
        require(width in 1..8192 && height in 1..8192) { "Invalid artwork dimensions: $slug" }
        slug to Triple(source, rom, artwork to (width.toFloat() / height))
    }
    val scenarios = JSONObject(scenariosText)
    val entries = scenarios.getJSONArray("entries").objects()
    require(entries.size == 14) { "Expected 12 games and two UI-only scenarios" }
    require(entries.map { it.getInt("id") }.distinct().size == entries.size) { "Duplicate scenario ID" }
    val realSlugs = entries.filterNot { it.optBoolean("uiOnly") }.map { it.getString("slug") }
    require(realSlugs.size == 12 && realSlugs.toSet() == verifiedSources.keys) { "Each source needs one real game entry" }
    return entries.map { entry ->
        val id = entry.getInt("id")
        require(id in 1..14) { "Invalid scenario ID" }
        val slug = entry.getString("slug")
        val (source, rom, art) = requireNotNull(verifiedSources[slug]) { "Unknown source: $slug" }
        val uiOnly = entry.optBoolean("uiOnly")
        val title = entry.optString("displayTitle", source.getString("title"))
        require(if (uiOnly) title.startsWith("[UI-only]") else title == source.getString("title")) {
            "Only labeled UI-only scenarios may override a source title"
        }
        val metadata = entry.optJSONObject("metadata")
        val game = GameEntity(
            id = DEMO_GAME_ID_BASE + id,
            platformId = DEMO_PLATFORM_ID,
            platformSlug = "nes",
            title = title,
            sortTitle = title,
            localPath = if (uiOnly) null else rom.absolutePath,
            rommId = null,
            igdbId = null,
            source = GameSource.LOCAL_ONLY,
            developer = if (metadata == null) null else source.optString("developer").ifBlank { null },
            releaseYear = metadata?.optInt("releaseYear")?.takeIf { it > 0 },
            genre = metadata?.optString("genre")?.ifBlank { null },
            players = metadata?.optString("players")?.ifBlank { null },
            rating = metadata?.optDouble("rating")?.takeIf { it.isFinite() }?.toFloat(),
            userRating = metadata?.optInt("userRating") ?: 0,
            userDifficulty = metadata?.optInt("difficulty") ?: 0,
            achievementCount = metadata?.optInt("achievements") ?: 0,
            earnedAchievementCount = metadata?.optInt("earnedAchievements") ?: 0,
            timeToBeatMainSec = metadata?.optInt("timeToBeatSeconds")?.takeIf { it > 0 },
            playTimeMinutes = metadata?.optInt("playTimeMinutes") ?: 0,
            isFavorite = entry.optBoolean("favorite"),
            description = scenarios.getString("notice") + "\n\n" + source.getString("sourceMetadata").take(900),
            screenshotPaths = art.first.absolutePath,
            cachedScreenshotPaths = art.first.absolutePath,
            hasFileOnDisk = !uiOnly,
            fileSizeBytes = if (uiOnly) null else rom.length(),
            storeEnrichStatus = GameEntity.STORE_SUCCESS
        )
        OfflineDemoFixture(game, art.first, art.second)
    }
}

private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

private fun verifyDemoFile(directory: File, slug: String, extension: String, files: List<JSONObject>): File {
    require(slug.matches(Regex("[a-z0-9-]{1,80}"))) { "Invalid fixture slug" }
    val name = "$slug.$extension"
    val metadata = files.single { it.getString("sourceUrl").endsWith("/$name") }
    val file = File(directory, name).canonicalFile
    require(file.parentFile == directory.canonicalFile && file.isFile) { "Missing or unsafe fixture: $name" }
    require(file.length() in 1L..MAX_FIXTURE_BYTES.toLong() && file.length() == metadata.getLong("bytes")) {
        "Invalid fixture size: $name"
    }
    val bytes = file.readBytes()
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(digest == metadata.getString("sha256")) { "Fixture hash mismatch: $name" }
    val signature = if (extension == "nes") byteArrayOf(0x4e, 0x45, 0x53, 0x1a)
        else byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    val minimumSize = if (extension == "nes") 16 else 24
    require(bytes.size >= minimumSize && bytes.take(signature.size).toByteArray().contentEquals(signature)) {
        "Invalid fixture header: $name"
    }
    return file
}
