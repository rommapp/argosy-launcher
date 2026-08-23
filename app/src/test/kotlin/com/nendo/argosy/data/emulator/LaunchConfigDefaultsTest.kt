package com.nendo.argosy.data.emulator

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the polymorphic defaults that each [LaunchConfig] subtype exposes. Adding a new subtype
 * means: add a case here and assert its categorical traits, instead of touching N call sites.
 */
class LaunchConfigDefaultsTest {

    private fun emulatorWithAction(action: String = Intent.ACTION_VIEW): EmulatorDef = EmulatorDef(
        id = "test",
        packageName = "test.pkg",
        displayName = "Test",
        supportedPlatforms = setOf("nes"),
        launchAction = action,
        launchConfig = LaunchConfig.FileUri
    )

    @Test
    fun `FileUri defaults grant URI permission and apply octet-stream mime`() {
        val config = LaunchConfig.FileUri
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            config.defaultIntentFlags(emulatorWithAction())
        )
        assertEquals("application/octet-stream", config.defaultMimeType)
        assertNull(config.defaultDataBinding)
        assertFalse(config.isInProcess)
        assertFalse(config.isCoreSelectable)
        assertFalse(config.requiresEmulatorKill)
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertEquals("FileProvider URI", bindings.data)
        assertEquals("FileProvider URI", bindings.clipData)
    }

    @Test
    fun `FilePathExtra has no mime, surfaces extra keys in label`() {
        val config = LaunchConfig.FilePathExtra(extraKeys = listOf("ROM", "rom"))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            config.defaultIntentFlags(emulatorWithAction())
        )
        assertNull(config.defaultMimeType)
        assertFalse(config.isCoreSelectable)
        assertFalse(config.isInProcess)
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertTrue(bindings.extras.contains("ROM"))
        assertTrue(bindings.extras.contains("rom"))
        assertEquals("None", bindings.data)
    }

    @Test
    fun `RetroArch is core-selectable and launches without history`() {
        val config = LaunchConfig.RetroArch()
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY,
            config.defaultIntentFlags(emulatorWithAction())
        )
        assertNull(config.defaultMimeType)
        assertTrue(config.isCoreSelectable)
        assertFalse(config.isInProcess)
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertEquals("Absolute path (ROM)", bindings.extras)
    }

    @Test
    fun `Custom mime falls back to octet-stream when no override`() {
        val config = LaunchConfig.Custom()
        assertEquals("application/octet-stream", config.defaultMimeType)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            config.defaultIntentFlags(emulatorWithAction())
        )
    }

    @Test
    fun `Custom drops the ROM from recents when it rides on extras`() {
        val config = LaunchConfig.Custom()
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY,
            config.defaultIntentFlags(emulatorWithAction(Intent.ACTION_MAIN))
        )
    }

    @Test
    fun `Custom mime override takes precedence`() {
        val config = LaunchConfig.Custom(mimeTypeOverride = "application/x-iso9660-image")
        assertEquals("application/x-iso9660-image", config.defaultMimeType)
    }

    @Test
    fun `Custom defaultDataBinding is preserved when set`() {
        val config = LaunchConfig.Custom(defaultDataBinding = RomBindingFormat.FILE_PROVIDER)
        assertEquals(RomBindingFormat.FILE_PROVIDER, config.defaultDataBinding)
    }

    @Test
    fun `Custom binding labels reflect ACTION_VIEW vs ACTION_MAIN`() {
        val viewBindings = LaunchConfig.Custom().bindingDefaults(emulatorWithAction(Intent.ACTION_VIEW))
        assertEquals("FileProvider URI", viewBindings.data)
        assertEquals("FileProvider URI", viewBindings.clipData)

        val mainBindings = LaunchConfig.Custom(
            intentExtras = mapOf("uri" to ExtraValue.FileUri)
        ).bindingDefaults(emulatorWithAction(Intent.ACTION_MAIN))
        assertEquals("None", mainBindings.data)
        assertTrue(mainBindings.extras.startsWith("FileProvider URI"))
        assertTrue(mainBindings.extras.contains("uri"))
    }

    @Test
    fun `CustomScheme locks the data row to its scheme prefix`() {
        val config = LaunchConfig.CustomScheme(scheme = "linkboy", authority = "emulator")
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY,
            config.defaultIntentFlags(emulatorWithAction())
        )
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertTrue(bindings.dataLocked)
        assertTrue(bindings.data.startsWith("linkboy"))
    }

    @Test
    fun `Vita3K requires emulator kill and locks the extras row`() {
        val config = LaunchConfig.Vita3K()
        assertTrue(config.requiresEmulatorKill)
        assertFalse(config.isCoreSelectable)
        assertFalse(config.isInProcess)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY,
            config.defaultIntentFlags(emulatorWithAction())
        )
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertTrue(bindings.extrasLocked)
    }

    @Test
    fun `BuiltIn is in-process, core-selectable, and reports N-A bindings`() {
        val config = LaunchConfig.BuiltIn
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, config.defaultIntentFlags(emulatorWithAction()))
        assertNull(config.defaultMimeType)
        assertTrue(config.isInProcess)
        assertTrue(config.isCoreSelectable)
        assertFalse(config.requiresEmulatorKill)
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertTrue(bindings.data.contains("in-process"))
    }

    @Test
    fun `ScummVM locks the data row to scummvm prefix`() {
        val config = LaunchConfig.ScummVM
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            config.defaultIntentFlags(emulatorWithAction())
        )
        assertNull(config.defaultMimeType)
        assertFalse(config.isCoreSelectable)
        val bindings = config.bindingDefaults(emulatorWithAction())
        assertTrue(bindings.dataLocked)
        assertTrue(bindings.data.startsWith("scummvm"))
    }

    @Test
    fun `every subtype produces a non-null BindingDefaults`() {
        val emulator = emulatorWithAction()
        val configs: List<LaunchConfig> = listOf(
            LaunchConfig.FileUri,
            LaunchConfig.FilePathExtra(),
            LaunchConfig.RetroArch(),
            LaunchConfig.Custom(),
            LaunchConfig.CustomScheme(scheme = "x", authority = "y"),
            LaunchConfig.Vita3K(),
            LaunchConfig.BuiltIn,
            LaunchConfig.ScummVM
        )
        configs.forEach { assertNotNull(it.bindingDefaults(emulator)) }
    }
}
