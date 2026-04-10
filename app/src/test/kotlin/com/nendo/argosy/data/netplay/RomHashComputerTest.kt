package com.nendo.argosy.data.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class RomHashComputerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun missingFile_returnsNull() {
        val missing = tempFolder.newFile("missing.tmp").also { it.delete() }
        assertNull(RomHashComputer.computeRomHashPrefix(missing))
    }

    @Test
    fun smallFile_hashesEntireContent() {
        val file = tempFolder.newFile("small.rom")
        val content = ByteArray(512) { it.toByte() }
        file.writeBytes(content)

        val expected = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }

        val actual = RomHashComputer.computeRomHashPrefix(file)
        assertEquals(expected, actual)
    }

    @Test
    fun largeFile_hashesFirstOneMB() {
        val file = tempFolder.newFile("large.rom")
        val content = ByteArray(4 * 1024 * 1024) { (it and 0xFF).toByte() }
        file.writeBytes(content)

        val expected = MessageDigest.getInstance("SHA-256").digest(
            content.copyOfRange(0, 1024 * 1024)
        ).joinToString("") { "%02x".format(it) }

        val actual = RomHashComputer.computeRomHashPrefix(file)
        assertEquals(expected, actual)
    }

    @Test
    fun stability_sameInputYieldsSameHash() {
        val file = tempFolder.newFile("stable.rom")
        file.writeBytes(ByteArray(1024) { 0x42 })

        val first = RomHashComputer.computeRomHashPrefix(file)
        val second = RomHashComputer.computeRomHashPrefix(file)
        assertNotNull(first)
        assertEquals(first, second)
    }
}
