package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val release = AppUpdateRelease(
        tag = "v3.1.0",
        name = "Argosy 3.1.0",
        notes = "## Fixes\n- one\n- two",
        assetName = "argosy-v3.1.0-arm64.apk",
        assetUrl = "https://example/argosy-v3.1.0-arm64.apk",
        assetSize = 42L,
        assetDigest = "sha256:00",
        abi = "arm64"
    )

    @Test
    fun `the check result and the skipped tag survive a new process`() {
        val file = File(folder.root, "update_state.json")
        UpdateStateStore(file).update { it.copy(latest = release, skippedTag = "v3.1.0") }

        val reloaded = UpdateStateStore(file).load()

        assertEquals(release, reloaded.latest)
        assertEquals("v3.1.0", reloaded.skippedTag)
    }

    @Test
    fun `an update before the first read keeps what was already stored`() {
        val file = File(folder.root, "update_state.json")
        UpdateStateStore(file).update { it.copy(skippedTag = "v3.0.5") }

        val next = UpdateStateStore(file).update { it.copy(latest = release) }

        assertEquals("v3.0.5", next.skippedTag)
        assertEquals(release, next.latest)
    }

    @Test
    fun `an unreadable file starts empty`() {
        val file = File(folder.root, "update_state.json").also { it.writeText("{not json") }
        val loaded = UpdateStateStore(file).load()
        assertNull(loaded.latest)
        assertNull(loaded.skippedTag)
    }

    @Test
    fun `a missing file starts empty`() {
        val loaded = UpdateStateStore(File(folder.root, "absent.json")).load()
        assertNull(loaded.latest)
        assertNull(loaded.skippedTag)
    }
}
