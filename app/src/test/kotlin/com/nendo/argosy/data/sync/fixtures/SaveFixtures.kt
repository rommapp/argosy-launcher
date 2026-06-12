package com.nendo.argosy.data.sync.fixtures

import java.io.File

/** Programmatic save-fixture builders keyed by save *shape*, not platform. */
object SaveFixtures {

    fun singleFile(dir: File, name: String = "game.srm", bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(bytes) }
    }

    fun flatFolder(dir: File, fileCount: Int = 3): File {
        dir.mkdirs()
        repeat(fileCount) { i ->
            File(dir, "save_$i.bin").writeBytes(ByteArray(64) { it.toByte() })
        }
        return dir
    }

    /** Switch titleId folder as Argosy-native zips would extract it. */
    fun switchTitleFolder(parent: File, titleId: String = "01007EF00011E000"): File {
        val folder = File(parent, titleId).apply { mkdirs() }
        File(folder, "progress.sav").writeBytes(ByteArray(2048) { it.toByte() })
        File(folder, "caption000.sav").writeBytes(ByteArray(1024))
        File(folder, "option.sav").writeBytes(ByteArray(256))
        File(folder, "0").mkdirs()
        File(folder, "0/slot0.sav").writeBytes(ByteArray(512))
        return folder
    }

    /** JKSV-format Switch save folder, with the .nx_save_meta.bin sentinel. */
    fun switchJksvFolder(parent: File, titleId: String = "01007EF00011E000"): File {
        val folder = switchTitleFolder(parent, titleId)
        File(folder, ".nx_save_meta.bin").writeBytes(jksvMetaBytes(titleId))
        return folder
    }

    /** Bytes layout: 4 "JKSV" + 1 pad + 8 LE titleId; matches SaveArchiver.parseTitleIdFromMeta. */
    fun jksvMetaBytes(titleId: String): ByteArray {
        require(titleId.length == 16) { "titleId must be 16 hex chars" }
        val titleIdLong = titleId.toULong(radix = 16).toLong()
        val bytes = ByteArray(13)
        byteArrayOf(0x4A, 0x4B, 0x53, 0x56).copyInto(bytes, 0)
        java.nio.ByteBuffer.wrap(bytes, 5, 8)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putLong(titleIdLong)
        return bytes
    }

    /** PSP-style sibling folders sharing a 9-char disc-id prefix. */
    fun pspPrefixedSiblings(parent: File, discId: String = "ULUS10064", suffixes: List<String> = listOf("DATA00", "SETTINGS", "SYSTEM")): File {
        parent.mkdirs()
        suffixes.forEach { suffix ->
            val sib = File(parent, "$discId$suffix").apply { mkdirs() }
            File(sib, "save.bin").writeBytes(ByteArray(128))
        }
        return parent
    }

    /** Sorted list of (relative-path, bytes) for every file under [root]. */
    fun fileTree(root: File): List<Pair<String, ByteArray>> =
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path to it.readBytes() }
            .sortedBy { it.first }
            .toList()

    /** Minimal GameCube ISO ROM with only the header bytes parseRomHeader reads. */
    fun gameCubeRom(file: File, gameId: String = "GZLE", makerCode: String = "01", name: String = "Zelda"): File {
        require(gameId.length == 4) { "gameId must be 4 chars" }
        require(makerCode.length == 2) { "makerCode must be 2 chars" }
        val bytes = ByteArray(0x100)
        gameId.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        makerCode.toByteArray(Charsets.US_ASCII).copyInto(bytes, 4)
        name.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x20)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    /** Minimal GCI save with only the header bytes parseGciHeader reads. */
    fun gciSave(
        file: File,
        gameId: String = "GZLE",
        makerCode: String = "01",
        internalFilename: String = "ZELDA_SAVE",
        payload: ByteArray = ByteArray(2048) { (it % 251).toByte() },
    ): File {
        require(gameId.length == 4) { "gameId must be 4 chars" }
        require(makerCode.length == 2) { "makerCode must be 2 chars" }
        require(internalFilename.length <= 32) { "internalFilename must fit in 32 bytes" }
        val bytes = ByteArray(0x40 + payload.size)
        gameId.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        makerCode.toByteArray(Charsets.US_ASCII).copyInto(bytes, 4)
        internalFilename.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x08)
        payload.copyInto(bytes, 0x40)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }
}
