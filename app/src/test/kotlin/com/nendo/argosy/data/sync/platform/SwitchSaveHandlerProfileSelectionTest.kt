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
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SwitchSaveHandlerProfileSelectionTest {

    private lateinit var tempDir: File
    private lateinit var handler: SwitchSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val profileParser = mockk<SwitchProfileParser>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("switch_profile_select").toFile()
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
    fun `returns basePath when basePath does not exist`() {
        val nonexistent = File(tempDir, "nope").absolutePath
        val result = handler.findActiveProfileFolder(nonexistent, emulatorPackage = null)
        assertEquals(nonexistent, result)
    }

    @Test
    fun `returns basePath when no valid user folder exists`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        File(base, "not_a_user_id").mkdirs()

        val result = handler.findActiveProfileFolder(base.absolutePath, emulatorPackage = null)

        assertEquals(base.absolutePath, result)
    }

    @Test
    fun `prefers non-zero profile over zero profile when only those two exist`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val user = File(base, "0000000000000000").apply { mkdirs() }
        File(user, "00000000000000000000000000000000").mkdirs()
        val nonZero = File(user, "FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(nonZero, "marker.bin").writeBytes(byteArrayOf(1)).also { File(nonZero, "marker.bin").setLastModified(1_000_000L) }

        val result = handler.findActiveProfileFolder(base.absolutePath, emulatorPackage = null)

        assertEquals(nonZero.absolutePath, result)
    }

    @Test
    fun `picks newest-modtime profile across multiple non-zero candidates`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val user = File(base, "0000000000000000").apply { mkdirs() }
        val older = File(user, "FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(older, "old.bin").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(1_000_000L)
        }
        val newer = File(user, "1111222233334444AAAA5555666677788").let {
            File(user, "AAAABBBBCCCCDDDDEEEEFFFF00001111").apply { mkdirs() }
        }
        File(newer, "new.bin").apply {
            writeBytes(byteArrayOf(2))
            setLastModified(System.currentTimeMillis())
        }

        val result = handler.findActiveProfileFolder(base.absolutePath, emulatorPackage = null)

        assertEquals(newer.absolutePath, result)
    }

    @Test
    fun `falls back to first non-zero profile when no files exist anywhere`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        val user = File(base, "0000000000000000").apply { mkdirs() }
        val zero = File(user, "00000000000000000000000000000000").apply { mkdirs() }
        val nonZero = File(user, "FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }

        val result = handler.findActiveProfileFolder(base.absolutePath, emulatorPackage = null)

        assertEquals(nonZero.absolutePath, result)
    }

    @Test
    fun `ignores child folders that fail user-id validation`() {
        val base = File(tempDir, "save").apply { mkdirs() }
        File(base, "not_hex_user_id").apply { mkdirs() }
        val validUser = File(base, "0000000000000000").apply { mkdirs() }
        val profile = File(validUser, "FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(profile, "marker.bin").writeBytes(byteArrayOf(1))

        val result = handler.findActiveProfileFolder(base.absolutePath, emulatorPackage = null)

        assertEquals(profile.absolutePath, result)
    }
}
