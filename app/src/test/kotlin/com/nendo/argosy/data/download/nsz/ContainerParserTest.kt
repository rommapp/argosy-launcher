package com.nendo.argosy.data.download.nsz

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

class ContainerParserTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("container_parser_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `parsePfs0 reads single file entry`() {
        val fileName = "test.nca"
        val fileData = ByteArray(1024) { it.toByte() }
        val pfs0 = buildPfs0(
            listOf(Pair(fileName, fileData))
        )

        val testFile = File(tempDir, "test.nsp")
        testFile.writeBytes(pfs0)

        val raf = RandomAccessFile(testFile, "r")
        val entries = ContainerParser.parsePfs0(raf)
        raf.close()

        assertEquals(1, entries.size)
        assertEquals("test.nca", entries[0].name)
        assertEquals(fileData.size.toLong(), entries[0].size)
        assertFalse(entries[0].isNcz)
        assertEquals("test.nca", entries[0].outputName)
    }

    @Test
    fun `parsePfs0 reads multiple entries with NCZ detection`() {
        val files = listOf(
            Pair("game.ncz", ByteArray(2048)),
            Pair("update.nca", ByteArray(512)),
            Pair("dlc.ncz", ByteArray(1024))
        )
        val pfs0 = buildPfs0(files)

        val testFile = File(tempDir, "test.nsz")
        testFile.writeBytes(pfs0)

        val raf = RandomAccessFile(testFile, "r")
        val entries = ContainerParser.parsePfs0(raf)
        raf.close()

        assertEquals(3, entries.size)

        assertTrue(entries[0].isNcz)
        assertEquals("game.nca", entries[0].outputName)

        assertFalse(entries[1].isNcz)
        assertEquals("update.nca", entries[1].outputName)

        assertTrue(entries[2].isNcz)
        assertEquals("dlc.nca", entries[2].outputName)
    }

    @Test
    fun `parsePfs0 computes correct data offsets`() {
        val files = listOf(
            Pair("a.nca", ByteArray(100)),
            Pair("b.nca", ByteArray(200))
        )
        val pfs0 = buildPfs0(files)

        val testFile = File(tempDir, "test.nsp")
        testFile.writeBytes(pfs0)

        val raf = RandomAccessFile(testFile, "r")
        val entries = ContainerParser.parsePfs0(raf)

        val aData = ByteArray(100)
        raf.seek(entries[0].dataOffset)
        raf.readFully(aData)

        val bData = ByteArray(200)
        raf.seek(entries[1].dataOffset)
        raf.readFully(bData)
        raf.close()

        assertEquals(
            entries[0].dataOffset + 100,
            entries[1].dataOffset
        )
    }

    @Test
    fun `computePfs0Header produces valid parseable header`() {
        val originalFiles = listOf(
            Pair("game.ncz", ByteArray(100)),
            Pair("update.nca", ByteArray(200))
        )
        val pfs0 = buildPfs0(originalFiles)
        val testFile = File(tempDir, "test.nsz")
        testFile.writeBytes(pfs0)

        val raf = RandomAccessFile(testFile, "r")
        val entries = ContainerParser.parsePfs0(raf)
        raf.close()

        val newSizes = listOf(500L, 200L)
        val newHeader = ContainerParser.computePfs0Header(
            entries, newSizes
        )

        val headerFile = File(tempDir, "header.bin")
        headerFile.writeBytes(newHeader + ByteArray(700))

        val raf2 = RandomAccessFile(headerFile, "r")
        val reEntries = ContainerParser.parsePfs0(raf2)
        raf2.close()

        assertEquals(2, reEntries.size)
        assertEquals("game.nca", reEntries[0].name)
        assertEquals(500L, reEntries[0].size)
        assertEquals("update.nca", reEntries[1].name)
        assertEquals(200L, reEntries[1].size)
    }

    private fun buildPfs0(
        files: List<Pair<String, ByteArray>>
    ): ByteArray {
        val stringTable = buildStringTable(files.map { it.first })
        val headerSize = 16 + (files.size * 24) + stringTable.size

        val buf = ByteBuffer.allocate(
            headerSize + files.sumOf { it.second.size }
        ).order(ByteOrder.LITTLE_ENDIAN)

        // PFS0 header
        buf.putInt(0x30534650) // "PFS0"
        buf.putInt(files.size)
        buf.putInt(stringTable.size)
        buf.putInt(0)

        var dataOffset = 0L
        var stringOffset = 0
        for ((name, data) in files) {
            buf.putLong(dataOffset)
            buf.putLong(data.size.toLong())
            buf.putInt(stringOffset)
            buf.putInt(0)
            dataOffset += data.size
            stringOffset += name.toByteArray().size + 1
        }

        buf.put(stringTable)

        for ((_, data) in files) {
            buf.put(data)
        }

        return buf.array()
    }

    private fun buildStringTable(names: List<String>): ByteArray {
        val sb = mutableListOf<Byte>()
        for (name in names) {
            sb.addAll(name.toByteArray(Charsets.US_ASCII).toList())
            sb.add(0)
        }
        return sb.toByteArray()
    }
}
