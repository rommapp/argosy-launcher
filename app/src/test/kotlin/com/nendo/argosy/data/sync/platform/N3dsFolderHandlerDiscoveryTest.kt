package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class N3dsFolderHandlerDiscoveryTest {

    private lateinit var tempDir: File
    private lateinit var handler: PlatformSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val id0 = "abcdef0123456789abcdef0123456789"
    private val id1 = "fedcba9876543210fedcba9876543210"
    private val category = "00040000"
    private val titleId = "0004000000175E00"
    private val shortTitleId = titleId.takeLast(8)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("n3ds_discovery").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = archiver,
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("3ds") ?: error("3DS handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `walks id0 id1 title category to find shortTitleId data folder`() {
        val data = File(tempDir, "$id0/$id1/title/$category/$shortTitleId/data").apply { mkdirs() }
        File(data, "save.bin").writeBytes(byteArrayOf(1, 2, 3))

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, titleId)

        assertEquals(data.absolutePath, result)
    }

    @Test
    fun `matches when caller passes lowercase titleId`() {
        val data = File(tempDir, "$id0/$id1/title/$category/$shortTitleId/data").apply { mkdirs() }
        File(data, "save.bin").writeBytes(byteArrayOf(1))

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, titleId.lowercase())

        assertEquals(data.absolutePath, result)
    }

    @Test
    fun `picks the data folder with the newest file when two candidates exist`() {
        val older = File(tempDir, "$id0/$id1/title/$category/$shortTitleId/data").apply { mkdirs() }
        File(older, "old.bin").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(1_000_000L)
        }
        val id0b = "1111222233334444aaaabbbbccccdddd"
        val id1b = "5555666677778888eeeeffff00001111"
        val newer = File(tempDir, "$id0b/$id1b/title/$category/$shortTitleId/data").apply { mkdirs() }
        File(newer, "new.bin").apply {
            writeBytes(byteArrayOf(2))
            setLastModified(System.currentTimeMillis())
        }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, titleId)

        assertEquals(newer.absolutePath, result)
    }

    @Test
    fun `returns null when no matching shortTitleId exists`() {
        File(tempDir, "$id0/$id1/title/$category/00000000/data").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, titleId)

        assertNull(result)
    }

    @Test
    fun `returns null when the matched shortTitleId folder lacks a data subfolder`() {
        File(tempDir, "$id0/$id1/title/$category/$shortTitleId").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, titleId)

        assertNull(result)
    }
}
