package com.nendo.argosy.data.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FileAccessLayerUnionNormalizationTest {

    private lateinit var tempDir: File
    private val context = mockk<Context>(relaxed = true)
    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val managedStorageAccessor = mockk<ManagedStorageAccessor>(relaxed = true)

    private lateinit var fal: FileAccessLayerImpl

    private val androidPath = "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save"
    private val altPath = "/storage/emulated/0/UCData/data/dev.eden.eden_emulator/files/nand/user/save"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("fal_union_norm_test").toFile()
        every { context.filesDir } returns tempDir
        every { androidDataAccessor.isRestrictedAndroidPath(any()) } answers {
            val p = firstArg<String>()
            p.contains("/Android/data/") || p.contains("/UCData/data/")
        }
        every { androidDataAccessor.normalizePathForDisplay(any()) } answers {
            firstArg<String>().replace("/UCData/", "/Android/")
        }
        every { managedStorageAccessor.listFiles(any(), any()) } returns null
        fal = FileAccessLayerImpl(context, androidDataAccessor, managedStorageAccessor)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `union returns normalized Android path when alt-access lists alt path entries`() {
        val altChild = File("$altPath/0000000000000000")
        every { androidDataAccessor.isAltAccessSupported() } returns true
        every { androidDataAccessor.listFiles(androidPath) } returns arrayOf(altChild)

        val result = fal.listFilesUnion(androidPath)

        assertEquals(1, result.size)
        assertEquals("/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save/0000000000000000", result[0].path)
    }

    @Test
    fun `union skips alt-access branch when alt not supported`() {
        every { androidDataAccessor.isAltAccessSupported() } returns false
        every { androidDataAccessor.listFiles(any()) } returns null

        val result = fal.listFilesUnion(androidPath)

        assertEquals(0, result.size)
    }
}
