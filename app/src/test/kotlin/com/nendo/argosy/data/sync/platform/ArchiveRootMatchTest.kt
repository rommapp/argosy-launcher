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

/**
 * A downloaded archive is unpacked over the user's live save directory, so it has to be
 * shown to belong to that save first. Sigil addresses a save exactly or by prefix, so both
 * are accepted, with a contains match as the weakest tier and no match refused outright.
 */
class ArchiveRootMatchTest {

    private lateinit var tempDir: File
    private lateinit var handler: FolderSaveHandler
    private lateinit var n3dsHandler: FolderSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("archive_root").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = SaveArchiver(androidDataAccessor, fal),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("ps2") as? FolderSaveHandler
            ?: error("PS2 handler not registered")
        n3dsHandler = registry.getFolderHandler("3ds") ?: error("3DS handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `an identical folder name is an exact match`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.EXACT,
            handler.matchArchiveRoot("BASLUS-20152", "BASLUS-20152")
        )
    }

    @Test
    fun `separators do not affect an exact match`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.EXACT,
            handler.matchArchiveRoot("BASLUS20152", "BASLUS-20152")
        )
    }

    @Test
    fun `a per-artifact suffix is a prefix match`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.PREFIX,
            handler.matchArchiveRoot("BASLUS-20152AC04", "BASLUS-20152")
        )
    }

    @Test
    fun `an embedded save id is the weakest accepted tier`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.CONTAINS,
            handler.matchArchiveRoot("backup_BASLUS-20152AC04", "BASLUS-20152AC04")
        )
    }

    @Test
    fun `an unrelated folder matches nothing`() {
        assertNull(handler.matchArchiveRoot("BASLUS-21050", "BASLUS-20152"))
    }

    @Test
    fun `a mis-rooted archive folder matches nothing`() {
        assertNull(handler.matchArchiveRoot("data", "0011C400"))
    }

    @Test
    fun `an empty root cannot stand in for a save id`() {
        assertNull(handler.matchArchiveRoot("", "BASLUS-20152"))
    }

    @Test
    fun `the 3ds data root is accepted as unidentified`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.UNIDENTIFIED,
            n3dsHandler.matchArchiveRoot("data", "0004000000033500")
        )
    }

    @Test
    fun `the 3ds extdata root is accepted as unidentified`() {
        assertEquals(
            FolderSaveHandler.ArchiveRootMatch.UNIDENTIFIED,
            n3dsHandler.matchArchiveRoot("extdata", "00040000/00113200")
        )
    }

    @Test
    fun `a 3ds archive rooted at anything else is still refused`() {
        assertNull(n3dsHandler.matchArchiveRoot("saves", "0004000000033500"))
        assertNull(n3dsHandler.matchArchiveRoot("0004000000033501", "0004000000033500"))
    }
}
