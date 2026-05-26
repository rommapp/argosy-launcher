package com.nendo.argosy.data.sync.fixtures

import java.io.File

/**
 * Programmatic save-fixture builders. Each builder generates the canonical on-disk layout
 * for a single save *shape*, not a specific platform. Multiple emulators may share a shape
 * (Switch + variants share [switchTitleFolder]); different cores of the same platform may
 * write different shapes (libretro PS2 cores vs standalone PCSX2 — open question, see
 * task #19). Add a new shape only when an actual on-device sample doesn't fit an existing
 * one.
 */
object SaveFixtures {

    /** Single .srm-style file with arbitrary bytes. RetroArch-shaped. */
    fun singleFile(dir: File, name: String = "game.srm", bytes: ByteArray = byteArrayOf(1, 2, 3, 4)): File {
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(bytes) }
    }

    /** Flat folder containing N files. 3DS sdmc-style. */
    fun flatFolder(dir: File, fileCount: Int = 3): File {
        dir.mkdirs()
        repeat(fileCount) { i ->
            File(dir, "save_$i.bin").writeBytes(ByteArray(64) { it.toByte() })
        }
        return dir
    }

    /**
     * Switch titleId folder as Argosy-native zips would extract it. The folder name is
     * the titleId; contents are the per-game save files Eden/yuzu write inside.
     */
    fun switchTitleFolder(parent: File, titleId: String = "01007EF00011E000"): File {
        val folder = File(parent, titleId).apply { mkdirs() }
        File(folder, "progress.sav").writeBytes(ByteArray(2048) { it.toByte() })
        File(folder, "caption000.sav").writeBytes(ByteArray(1024))
        File(folder, "option.sav").writeBytes(ByteArray(256))
        File(folder, "0").mkdirs()
        File(folder, "0/slot0.sav").writeBytes(ByteArray(512))
        return folder
    }

    /**
     * JKSV-style Switch save: titleId folder containing a .nx_save_meta.bin sentinel
     * alongside the save files. Used to test JKSV-format detection + structure
     * preservation through extract.
     */
    fun switchJksvFolder(parent: File, titleId: String = "01007EF00011E000"): File {
        val folder = switchTitleFolder(parent, titleId)
        File(folder, ".nx_save_meta.bin").writeBytes(byteArrayOf(0x4A, 0x4B, 0x53, 0x56))
        return folder
    }

    /**
     * PSP-style sibling folders sharing a 9-char disc-id prefix. Returns the parent
     * folder; each child is one "save unit" (DATA00, SETTINGS, etc.).
     */
    fun pspPrefixedSiblings(parent: File, discId: String = "ULUS10064", suffixes: List<String> = listOf("DATA00", "SETTINGS", "SYSTEM")): File {
        parent.mkdirs()
        suffixes.forEach { suffix ->
            val sib = File(parent, "$discId$suffix").apply { mkdirs() }
            File(sib, "save.bin").writeBytes(ByteArray(128))
        }
        return parent
    }

    /** Returns all files (not directories) under [root], sorted, as relative paths. */
    fun fileTree(root: File): List<Pair<String, ByteArray>> =
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).path to it.readBytes() }
            .sortedBy { it.first }
            .toList()

    /**
     * Minimal valid GameCube ISO ROM image. Only the header bytes that
     * GameCubeHeaderParser.parseRomHeader inspects are populated:
     * - 0x00..0x03: 4-byte game id
     * - 0x04..0x05: 2-byte maker code
     * - 0x20..0x5F: null-terminated game name (64 bytes)
     */
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

    /**
     * Minimal valid GCI save image. Parser reads:
     * - 0x00..0x03: 4-byte game id
     * - 0x04..0x05: 2-byte maker code
     * - 0x08..0x27: 32-byte null-terminated internal filename
     * The remainder is opaque payload bytes.
     */
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
