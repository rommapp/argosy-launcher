package com.nendo.argosy.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtCacheSweepTest {

    private val root = "/data/user/0/app/files/images/snes"
    private val old = 1_000L
    private val cutoff = 5_000L

    private fun file(dir: String, name: String, modified: Long = old) =
        ArtCacheFile("$root/$dir/$name", name, modified)

    @Test
    fun `a cover file no row references is an orphan`() {
        val orphan = file("covers", "cover_42_abc.jpg")

        val plan = planArtSweep(listOf(orphan), emptyList(), emptyList(), cutoff)

        assertEquals(listOf(orphan.path), plan.orphanFiles)
    }

    @Test
    fun `a file a cached path references is kept`() {
        val kept = file("covers", "cover_42_abc.jpg")

        val plan = planArtSweep(listOf(kept), listOf(kept.path), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
        assertTrue(plan.missingCachedPaths.isEmpty())
    }

    @Test
    fun `override files are never orphans even without a row`() {
        val overrides = listOf(
            file("covers", "cover_override_7_a.jpg"),
            file("covers", "cover_manual_7_a.jpg"),
            file("backgrounds", "bg_override_7_b.jpg"),
            file("backgrounds", "bg_custom_7_b.jpg"),
            file("logos", "logo_override_7_c.png")
        )

        val plan = planArtSweep(overrides, emptyList(), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
    }

    @Test
    fun `local-only art the sweep does not own is never an orphan`() {
        val localOnly = listOf(
            file("icons", "appicon_123.png"),
            file("screenshots", "uss_42_9_abc.png"),
            file("covers", "box_back_42_abc.jpg"),
            file("covers", "box_spine_42_abc.jpg"),
            file("badges", "badge_9_abc.png")
        )

        val plan = planArtSweep(localOnly, emptyList(), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
    }

    @Test
    fun `every swept prefix is collected when unreferenced`() {
        val swept = listOf(
            file("covers", "cover_g7_abc.jpg"),
            file("backgrounds", "bg_42_abc.jpg"),
            file("backgrounds", "steam_bg_620_abc.jpg"),
            file("covers", "game_logo_42_abc.png"),
            file("screenshots", "ss_42_0_abc.jpg"),
            file("screenshots", "ss_g7_1_abc.jpg")
        )

        val plan = planArtSweep(swept, emptyList(), emptyList(), cutoff)

        assertEquals(swept.map { it.path }.toSet(), plan.orphanFiles.toSet())
    }

    @Test
    fun `a screenshot file a screenshot row references is kept`() {
        val kept = file("screenshots", "ss_42_0_abc.jpg")

        val plan = planArtSweep(listOf(kept), listOf(kept.path), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
    }

    @Test
    fun `user screenshots are never swept even with nothing referencing them`() {
        val userShots = listOf(
            file("screenshots", "uss_42_9_abc.png"),
            file("screenshots", "uss_g7_1_abc.png")
        )

        val plan = planArtSweep(userShots, emptyList(), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
        userShots.forEach { assertFalse(isSweptArtFileName(it.name)) }
    }

    @Test
    fun `a file written after the cutoff is left for a later pass`() {
        val fresh = file("covers", "cover_42_new.jpg", modified = cutoff)

        val plan = planArtSweep(listOf(fresh), emptyList(), emptyList(), cutoff)

        assertTrue(plan.orphanFiles.isEmpty())
    }

    @Test
    fun `a referenced path with no file is missing`() {
        val present = file("covers", "cover_42_abc.jpg")
        val gone = "$root/covers/cover_43_def.jpg"

        val plan = planArtSweep(listOf(present), listOf(present.path, gone), emptyList(), cutoff)

        assertEquals(setOf(gone), plan.missingCachedPaths)
    }

    @Test
    fun `a missing override is reported apart from missing cached paths`() {
        val gone = "$root/covers/cover_override_7_a.jpg"

        val plan = planArtSweep(emptyList(), emptyList(), listOf(gone), cutoff)

        assertEquals(setOf(gone), plan.missingOverridePaths)
        assertTrue(plan.missingCachedPaths.isEmpty())
    }

    @Test
    fun `a remote url in a path column is never reported missing`() {
        val plan = planArtSweep(emptyList(), listOf("https://romm/covers/42.png"), emptyList(), cutoff)

        assertTrue(plan.missingCachedPaths.isEmpty())
    }

    @Test
    fun `swept names are recognised by prefix`() {
        assertTrue(isSweptArtFileName("cover_42_abc.jpg"))
        assertTrue(isSweptArtFileName("bg_g7_abc.jpg"))
        assertFalse(isSweptArtFileName("cover_override_7_abc.jpg"))
        assertFalse(isSweptArtFileName("logo_12_abc.png"))
        assertTrue(isSweptArtFileName("ss_42_0_abc.jpg"))
        assertFalse(isSweptArtFileName("uss_42_9_abc.png"))
    }
}
