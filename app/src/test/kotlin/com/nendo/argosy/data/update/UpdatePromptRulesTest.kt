package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePromptRulesTest {

    private fun release(tag: String) = AppUpdateRelease(
        tag = tag,
        name = "Argosy $tag",
        notes = "notes",
        assetName = "argosy-$tag-arm64.apk",
        assetUrl = "https://example/argosy-$tag-arm64.apk",
        assetSize = 10L,
        assetDigest = null,
        abi = "arm64"
    )

    @Test
    fun `a newer release prompts when nothing is skipped or deferred`() {
        val latest = release("v3.1.0")
        assertEquals(latest, UpdatePromptRules.promptFor(latest, null, emptySet(), "3.0.0"))
    }

    @Test
    fun `the skipped tag never prompts`() {
        val latest = release("v3.1.0")
        assertNull(UpdatePromptRules.promptFor(latest, "v3.1.0", emptySet(), "3.0.0"))
    }

    @Test
    fun `a release newer than the skipped tag prompts`() {
        val latest = release("v3.2.0")
        assertEquals(latest, UpdatePromptRules.promptFor(latest, "v3.1.0", emptySet(), "3.0.0"))
    }

    @Test
    fun `a skipped tag older than the latest does not suppress it`() {
        val latest = release("v3.1.0")
        assertEquals(latest, UpdatePromptRules.promptFor(latest, "v3.0.5", emptySet(), "3.0.0"))
    }

    @Test
    fun `later suppresses only the deferred tag`() {
        val deferred = setOf("v3.1.0")
        assertNull(UpdatePromptRules.promptFor(release("v3.1.0"), null, deferred, "3.0.0"))
        val newer = release("v3.2.0")
        assertEquals(newer, UpdatePromptRules.promptFor(newer, null, deferred, "3.0.0"))
    }

    @Test
    fun `later is forgotten with the deferred set at the next start`() {
        val latest = release("v3.1.0")
        assertNull(UpdatePromptRules.promptFor(latest, null, setOf("v3.1.0"), "3.0.0"))
        assertEquals(latest, UpdatePromptRules.promptFor(latest, null, emptySet(), "3.0.0"))
    }

    @Test
    fun `a release at or below the installed version never prompts`() {
        assertNull(UpdatePromptRules.promptFor(release("v3.0.0"), null, emptySet(), "3.0.0"))
        assertNull(UpdatePromptRules.promptFor(release("v2.9.0"), null, emptySet(), "3.0.0"))
        assertNull(UpdatePromptRules.promptFor(null, null, emptySet(), "3.0.0"))
    }

    @Test
    fun `a stable release prompts over its own prerelease`() {
        val latest = release("v3.0.0")
        assertEquals(latest, UpdatePromptRules.promptFor(latest, null, emptySet(), "3.0.0-beta.1"))
    }

    @Test
    fun `about still lists a skipped release as available`() {
        val latest = release("v3.1.0")
        assertEquals(latest, UpdatePromptRules.available(latest, "3.0.0"))
        assertNull(UpdatePromptRules.promptFor(latest, "v3.1.0", emptySet(), "3.0.0"))
    }

    @Test
    fun `release builds always check and debug builds only with the developer option`() {
        assertTrue(UpdatePromptRules.checksAllowed(isDebugBuild = false, debugChecksEnabled = false))
        assertTrue(UpdatePromptRules.checksAllowed(isDebugBuild = false, debugChecksEnabled = true))
        assertFalse(UpdatePromptRules.checksAllowed(isDebugBuild = true, debugChecksEnabled = false))
        assertTrue(UpdatePromptRules.checksAllowed(isDebugBuild = true, debugChecksEnabled = true))
    }
}
