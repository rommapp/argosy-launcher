package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.sync.fixtures.realFsFal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class Pcsx2DataFolderTest {

    private lateinit var tempDir: File
    private val fal = realFsFal()

    @Before
    fun setUp() {
        tempDir = createTempDirectory("pcsx2_data").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private val thorIni = """
        [Folders]
        MemoryCards = memcards

        [MemoryCards]
        Slot1_Enable = true
        Slot1_Filename = maincard
        Slot2_Enable = true
        Slot2_Filename = mcd002.ps2
    """.trimIndent()

    @Test
    fun `slot 1 and the cards folder are read from the ini`() {
        val settings = Pcsx2DataFolder.parse(thorIni)

        assertEquals("memcards", settings.memoryCardsFolder)
        assertEquals("maincard", settings.slot1Card)
    }

    @Test
    fun `an ini without the sections names nothing`() {
        val settings = Pcsx2DataFolder.parse("[EmuCore]\nEnableCheats = false\n")

        assertNull(settings.memoryCardsFolder)
        assertNull(settings.slot1Card)
    }

    @Test
    fun `a disabled slot 1 names no card`() {
        val settings = Pcsx2DataFolder.parse("[MemoryCards]\nSlot1_Enable = false\nSlot1_Filename = maincard\n")

        assertNull(settings.slot1Card)
    }

    @Test
    fun `a slot 1 with no enable key is enabled`() {
        val settings = Pcsx2DataFolder.parse("[MemoryCards]\nSlot1_Filename = Mcd001.ps2\n")

        assertEquals("Mcd001.ps2", settings.slot1Card)
    }

    @Test
    fun `extra spaces around keys and values are dropped`() {
        val settings = Pcsx2DataFolder.parse("[Folders]\n  MemoryCards   =    cards  \n[MemoryCards]\n\tSlot1_Filename\t=   maincard   \n")

        assertEquals("cards", settings.memoryCardsFolder)
        assertEquals("maincard", settings.slot1Card)
    }

    @Test
    fun `a slot 1 key in another section is not slot 1`() {
        val settings = Pcsx2DataFolder.parse("[Other]\nSlot1_Filename = maincard\n")

        assertNull(settings.slot1Card)
    }

    @Test
    fun `the cards folder defaults to memcards and honours an absolute path`() {
        assertEquals("/data/root/memcards", Pcsx2DataFolder.cardsDir("/data/root/", null))
        assertEquals("/data/root/cards", Pcsx2DataFolder.cardsDir("/data/root", Pcsx2Settings("cards", null)))
        assertEquals("/elsewhere/cards", Pcsx2DataFolder.cardsDir("/data/root", Pcsx2Settings("/elsewhere/cards/", null)))
    }

    @Test
    fun `the card base points slot 1 into the cards folder`() {
        val base = Pcsx2DataFolder.cardBase("/data/root", Pcsx2DataFolder.parse(thorIni))

        assertEquals("/data/root/memcards", base.cardsDir)
        assertEquals("/data/root/memcards/maincard", base.activeCard)
    }

    @Test
    fun `the ini is found in inis or at the top of the data folder`() {
        val inInis = File(tempDir, "a").apply { File(this, "inis").mkdirs() }
        File(inInis, "inis/${Pcsx2DataFolder.INI_NAME}").writeText(thorIni)
        val atTop = File(tempDir, "b").apply { mkdirs() }
        File(atTop, Pcsx2DataFolder.INI_NAME).writeText(thorIni)
        val none = File(tempDir, "c").apply { mkdirs() }

        assertEquals("maincard", Pcsx2DataFolder.read(inInis.absolutePath, fal)?.slot1Card)
        assertEquals("maincard", Pcsx2DataFolder.read(atTop.absolutePath, fal)?.slot1Card)
        assertNull(Pcsx2DataFolder.read(none.absolutePath, fal))
    }

    @Test
    fun `a path on one volume is placed on every volume`() {
        val placed = Pcsx2DataFolder.acrossVolumes(
            listOf("/storage/emulated/0/Android/data/com.armsx2/files/memcards", "/other/memcards"),
            listOf("/storage/emulated/0", "/storage/546B-6466")
        )

        assertEquals(
            listOf(
                "/storage/emulated/0/Android/data/com.armsx2/files/memcards",
                "/storage/546B-6466/Android/data/com.armsx2/files/memcards",
                "/other/memcards"
            ),
            placed
        )
    }

    @Test
    fun `the live cards folder is the one whose data folder holds the ini`() {
        val internal = File(tempDir, "internal/files").apply { mkdirs() }
        File(internal, "memcards/Shared.ps2").mkdirs()
        val sd = File(tempDir, "sd/files").apply { mkdirs() }
        File(sd, Pcsx2DataFolder.INI_NAME).writeText(thorIni)

        val live = Pcsx2DataFolder.liveCardsDir(
            listOf("${internal.absolutePath}/memcards", "${sd.absolutePath}/memcards"),
            fal
        )

        assertEquals("${sd.absolutePath}/memcards", live)
    }

    @Test
    fun `the newest ini wins when two data folders hold one`() {
        val older = File(tempDir, "older/files").apply { mkdirs() }
        File(older, Pcsx2DataFolder.INI_NAME).apply { writeText(thorIni); setLastModified(1_000_000L) }
        val newer = File(tempDir, "newer/files").apply { mkdirs() }
        File(newer, Pcsx2DataFolder.INI_NAME).apply { writeText(thorIni); setLastModified(2_000_000L) }

        val live = Pcsx2DataFolder.liveCardsDir(
            listOf("${older.absolutePath}/memcards", "${newer.absolutePath}/memcards"),
            fal
        )

        assertEquals("${newer.absolutePath}/memcards", live)
    }

    private val armsx2DefaultIni = """
        [Folders]
        MemoryCards = memcards

        [MemoryCards]
        Slot1_Enable = true
        Slot1_Filename = mcd001.ps2
    """.trimIndent()

    private fun dataFolder(name: String, globalIni: String = armsx2DefaultIni): File =
        File(tempDir, name).apply {
            File(this, "gamesettings").mkdirs()
            File(this, Pcsx2DataFolder.INI_NAME).writeText(globalIni)
        }

    @Test
    fun `a game's own card overrides the global slot 1`() {
        val root = dataFolder("game")
        File(root, "gamesettings/SLUS-20675_ABCD1234.ini")
            .writeText("[EmuCore/GS]\nupscale_multiplier = 2\n\n[MemoryCards]\nSlot1_Filename = DarkCloud\n")

        val settings = Pcsx2DataFolder.read(root.absolutePath, fal, "BASLUS-20675")

        assertEquals("DarkCloud", settings?.slot1Card)
        assertEquals("${root.absolutePath}/memcards/DarkCloud", Pcsx2DataFolder.cardBase(root.absolutePath, settings).activeCard)
    }

    @Test
    fun `without a serial the global slot 1 stands`() {
        val root = dataFolder("noserial")
        File(root, "gamesettings/SLUS-20675_ABCD1234.ini").writeText("[MemoryCards]\nSlot1_Filename = DarkCloud\n")

        assertEquals("mcd001.ps2", Pcsx2DataFolder.read(root.absolutePath, fal)?.slot1Card)
    }

    @Test
    fun `another game's ini does not change slot 1`() {
        val root = dataFolder("other")
        File(root, "gamesettings/SLUS-21050_11112222.ini").writeText("[MemoryCards]\nSlot1_Filename = Other\n")

        assertEquals("mcd001.ps2", Pcsx2DataFolder.read(root.absolutePath, fal, "SLUS-20675")?.slot1Card)
    }

    @Test
    fun `a game ini without a card keeps the global slot 1`() {
        val root = dataFolder("nocard")
        File(root, "gamesettings/SLUS-20675_ABCD1234.ini").writeText("[EmuCore/GS]\nupscale_multiplier = 2\n")

        assertEquals("mcd001.ps2", Pcsx2DataFolder.read(root.absolutePath, fal, "SLUS-20675")?.slot1Card)
    }

    @Test
    fun `a game ini that disables slot 1 names no card`() {
        val root = dataFolder("disabled")
        File(root, "gamesettings/SLUS-20675_ABCD1234.ini").writeText("[MemoryCards]\nSlot1_Enable = false\n")

        assertNull(Pcsx2DataFolder.read(root.absolutePath, fal, "SLUS-20675")?.slot1Card)
    }

    @Test
    fun `the newest ini wins for a game on several discs`() {
        val root = dataFolder("discs")
        File(root, "gamesettings/SLUS-20675_00000001.ini").apply {
            writeText("[MemoryCards]\nSlot1_Filename = DiscOne\n")
            setLastModified(1_000_000L)
        }
        File(root, "gamesettings/SLUS-20675_00000002.ini").apply {
            writeText("[MemoryCards]\nSlot1_Filename = DiscTwo\n")
            setLastModified(2_000_000L)
        }

        assertEquals("DiscTwo", Pcsx2DataFolder.read(root.absolutePath, fal, "SLUS-20675")?.slot1Card)
    }

    @Test
    fun `serials match across dashes, case and the card's region prefix`() {
        assertEquals("SLUS20675", Pcsx2DataFolder.serialKey("SLUS-20675"))
        assertEquals("SLUS20675", Pcsx2DataFolder.serialKey("BASLUS-20675"))
        assertEquals("SLUS20675", Pcsx2DataFolder.serialKey("slus_206.75"))
        assertEquals("SCES50490", Pcsx2DataFolder.serialKey("BESCES-50490"))
    }

    @Test
    fun `only a name ending ps2 is a file card name`() {
        assertTrue(Pcsx2DataFolder.isFolderCardName("maincard"))
        assertFalse(Pcsx2DataFolder.isFolderCardName("Mcd001.ps2"))
        assertFalse(Pcsx2DataFolder.isFolderCardName("mcd001.PS2"))
    }
}
