package com.nendo.argosy.debugtools

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

class OfflineDemoFixturesTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `original ROMs retain titles while UI specimens remain uninstalled`() {
        val (directory, manifest, scenarios) = sourceFiles()
        writeManifest(directory, manifest)

        val result = readOfflineDemoFixtures(directory, scenarios.toString())

        assertEquals((1..12).map { "Source title $it" }, result.take(12).map { it.game.title })
        assertTrue(result.take(12).all { it.game.localPath != null && it.game.hasFileOnDisk })
        assertTrue(result.drop(12).all { it.game.localPath == null && !it.game.hasFileOnDisk })
        assertTrue(result.all { it.game.id > 0 && it.game.rommId == null })
        assertEquals(256f / 240f, result.first().aspectRatio, 0.0001f)
        assertTrue(result.first().game.isFavorite)
        assertFalse(result[1].game.isFavorite)
        assertNull(result[11].game.developer)
        assertNull(result[11].game.rating)
    }

    @Test
    fun `changed ROM bytes are rejected even when length is unchanged`() {
        val (directory, manifest, scenarios) = sourceFiles()
        writeManifest(directory, manifest)
        val rom = File(directory, "game-1.nes")
        rom.writeBytes(rom.readBytes().also { it[15] = 1 })

        assertThrows(IllegalArgumentException::class.java) {
            readOfflineDemoFixtures(directory, scenarios.toString())
        }
    }

    @Test
    fun `source slugs cannot escape the fixture directory`() {
        val (directory, manifest, scenarios) = sourceFiles()
        manifest.getJSONArray("games").getJSONObject(0).put("slug", "../outside")
        writeManifest(directory, manifest)

        assertThrows(IllegalArgumentException::class.java) {
            readOfflineDemoFixtures(directory, scenarios.toString())
        }
    }

    @Test
    fun `a source title cannot silently become a synthetic display title`() {
        val (directory, manifest, scenarios) = sourceFiles()
        scenarios.getJSONArray("entries").getJSONObject(0).put("displayTitle", "Different title")
        writeManifest(directory, manifest)

        assertThrows(IllegalArgumentException::class.java) {
            readOfflineDemoFixtures(directory, scenarios.toString())
        }
    }

    @Test
    fun `a duplicate scenario cannot target the same database row`() {
        val (directory, manifest, scenarios) = sourceFiles()
        scenarios.getJSONArray("entries").getJSONObject(1).put("id", 1)
        writeManifest(directory, manifest)

        assertThrows(IllegalArgumentException::class.java) {
            readOfflineDemoFixtures(directory, scenarios.toString())
        }
    }

    @Test
    fun `a matching hash does not excuse an invalid ROM header`() {
        val (directory, manifest, scenarios) = sourceFiles()
        val rom = File(directory, "game-1.nes")
        rom.writeBytes(ByteArray(16))
        manifest.getJSONArray("games").getJSONObject(0).getJSONArray("files")
            .getJSONObject(0).put("sha256", sha256(rom.readBytes()))
        writeManifest(directory, manifest)

        assertThrows(IllegalArgumentException::class.java) {
            readOfflineDemoFixtures(directory, scenarios.toString())
        }
    }

    private fun sourceFiles(): Triple<File, JSONObject, JSONObject> {
        val directory = temporary.newFolder()
        val sources = JSONArray()
        val entries = JSONArray()
        for (id in 1..12) {
            val slug = "game-$id"
            val rom = ByteArray(16).also { byteArrayOf(0x4e, 0x45, 0x53, 0x1a).copyInto(it) }
            val png = ByteArray(24).also {
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(it)
                ByteBuffer.wrap(it, 16, 8).putInt(256).putInt(240)
            }
            val files = JSONArray()
            for ((extension, bytes) in listOf("nes" to rom, "png" to png)) {
                File(directory, "$slug.$extension").writeBytes(bytes)
                files.put(JSONObject().put("sourceUrl", "https://example.test/$slug.$extension")
                    .put("sha256", sha256(bytes)).put("bytes", bytes.size))
            }
            sources.put(JSONObject().put("slug", slug).put("platformSlug", "nes")
                .put("title", "Source title $id").put("developer", "Source developer")
                .put("sourceMetadata", "Source notice").put("files", files))
            val entry = JSONObject().put("id", id).put("slug", slug).put("favorite", id == 1)
            if (id != 12) entry.put("metadata", JSONObject().put("rating", 80))
            entries.put(entry)
        }
        for (id in 13..14) {
            entries.put(JSONObject().put("id", id).put("slug", "game-1")
                .put("uiOnly", true).put("displayTitle", "[UI-only] Long title $id"))
        }
        return Triple(directory, JSONObject().put("games", sources),
            JSONObject().put("notice", "Synthetic metadata notice").put("entries", entries))
    }

    private fun writeManifest(directory: File, manifest: JSONObject) {
        File(directory, "seed-manifest.json").writeText(manifest.toString())
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
