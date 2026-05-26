package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SwitchSaveHandlerIdentityTest {

    private lateinit var tempDir: File
    private lateinit var handler: SwitchSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val profileParser = mockk<SwitchProfileParser>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("switch_identity").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        handler = SwitchSaveHandler(context, fal, archiver, profileParser)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isValidHexId accepts 16 hex chars in any case`() {
        assertTrue(handler.isValidHexId("01007EF00011E000"))
        assertTrue(handler.isValidHexId("01007ef00011e000"))
        assertTrue(handler.isValidHexId("0000000000000000"))
    }

    @Test
    fun `isValidHexId rejects wrong length`() {
        assertFalse(handler.isValidHexId(""))
        assertFalse(handler.isValidHexId("01007EF00011E00"))
        assertFalse(handler.isValidHexId("01007EF00011E0000"))
    }

    @Test
    fun `isValidHexId rejects non-hex chars`() {
        assertFalse(handler.isValidHexId("01007EF00011G000"))
        assertFalse(handler.isValidHexId("01007EF00011 000"))
    }

    @Test
    fun `isValidTitleId requires hex AND starts with 01`() {
        assertTrue(handler.isValidTitleId("01007EF00011E000"))
        assertFalse("Title IDs must start with 01", handler.isValidTitleId("FF007EF00011E000"))
        assertFalse(handler.isValidTitleId("0100"))
    }

    @Test
    fun `isValidUserFolderId accepts 16 hex chars regardless of leading digits`() {
        assertTrue(handler.isValidUserFolderId("0000000000000000"))
        assertTrue(handler.isValidUserFolderId("FD91DCDABCDEF012"))
    }

    @Test
    fun `isValidProfileFolderId accepts 16 or 32 hex chars`() {
        assertTrue(handler.isValidProfileFolderId("0000000000000000"))
        assertTrue(handler.isValidProfileFolderId("00000000000000000000000000000000"))
        assertFalse("24 chars is not a valid profile folder", handler.isValidProfileFolderId("000000000000000000000000"))
    }

    @Test
    fun `isDeviceSave matches BOTW DEVICE_SAVE_TITLE_IDS set members`() {
        assertFalse("BOTW is a regular title (not device save)", handler.isDeviceSave("01007EF00011E000"))
        assertTrue("Animal Crossing is in the device-save set", handler.isDeviceSave("01006F8002326000"))
        assertTrue("Lookup is case-insensitive", handler.isDeviceSave("01006f8002326000"))
    }

    @Test
    fun `isValidCachedSavePath requires nested user profile titleId structure`() {
        assertTrue(
            "Canonical 4-level nested path",
            handler.isValidCachedSavePath("/save/0000000000000000/FEDCBA9876543210FEDCBA9876543210/01007EF00011E000")
        )
        assertFalse(
            "Missing profile folder",
            handler.isValidCachedSavePath("/save/0000000000000000/01007EF00011E000")
        )
        assertFalse(
            "Invalid titleId (not 16 hex starting with 01)",
            handler.isValidCachedSavePath("/save/0000000000000000/FEDCBA9876543210FEDCBA9876543210/XX007EF00011E000")
        )
    }

    @Test
    fun `findOrCreateZeroProfileFolder creates the canonical zero path under basePath`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val created = handler.findOrCreateZeroProfileFolder(base.absolutePath)

        val expected = "${base.absolutePath}/0000000000000000/00000000000000000000000000000000"
        assertEquals(expected, created)
        assertTrue("Zero-profile folder must exist on disk", File(created).isDirectory)
    }

    @Test
    fun `constructSavePath places device-save title under the zero profile folder`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val deviceTitleId = "01006F8002326000"
        val path = handler.constructSavePath(base.absolutePath, deviceTitleId, emulatorPackage = null)

        assertEquals(
            "${base.absolutePath}/0000000000000000/00000000000000000000000000000000/$deviceTitleId",
            path,
        )
    }

    @Test
    fun `constructSavePath uses active profile for non-device titles`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val userDir = File(base, "0000000000000000").apply { mkdirs() }
        val profileDir = File(userDir, "FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(profileDir, "marker.bin").writeBytes(byteArrayOf(1))

        val path = handler.constructSavePath(base.absolutePath, "01007EF00011E000", emulatorPackage = null)

        assertEquals("${profileDir.absolutePath}/01007EF00011E000", path)
    }

    @Test
    fun `constructSavePath uppercases the titleId regardless of caller casing`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val deviceTitleId = "01006f8002326000"

        val path = handler.constructSavePath(base.absolutePath, deviceTitleId, emulatorPackage = null)

        assertTrue(
            "Title id in output must be uppercase",
            path.endsWith("/01006F8002326000"),
        )
    }
}
