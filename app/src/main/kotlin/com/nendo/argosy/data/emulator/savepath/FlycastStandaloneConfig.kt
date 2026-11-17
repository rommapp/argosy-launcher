package com.nendo.argosy.data.emulator.savepath

import com.nendo.argosy.data.storage.FileAccessLayer
import java.io.File
import java.net.URLDecoder

/**
 * The VMU settings standalone Flycast reads from `emu.cfg`, which sits beside its `data` folder.
 * [vmuDir] is the folder the VMUs live in, or null when `Dreamcast.VMUPath` names a location
 * that has no file path.
 */
data class FlycastStandaloneConfig(val perGameVmu: Boolean, val vmuDir: String?) {

    val sigilOptions: Map<String, String>
        get() = mapOf(PER_GAME_VMU to if (perGameVmu) "yes" else "no")

    companion object {
        const val PER_GAME_VMU = "PerGameVmu"
        private const val VMU_PATH = "Dreamcast.VMUPath"
        private const val SECTION = "config"
        private const val CONFIG_NAME = "emu.cfg"
        private val TRUE_VALUES = setOf("yes", "true", "on", "1")
        private const val TREE_PREFIX = "content://com.android.externalstorage.documents/tree/"
        private const val PRIMARY_VOLUME = "primary"
        private const val PRIMARY_ROOT = "/storage/emulated/0"

        fun read(dataDir: String, fal: FileAccessLayer): FlycastStandaloneConfig {
            val dir = dataDir.trimEnd('/')
            val configPath = "${File(dir).parent}/$CONFIG_NAME"
            val text = fal.getInputStream(configPath)?.use { it.bufferedReader().readText() }.orEmpty()
            return parse(text, dir)
        }

        /**
         * The settings in [text], an `emu.cfg`, read the way Flycast's IniFile reads them: an
         * absent `PerGameVmu` is on, and an empty `Dreamcast.VMUPath` leaves the VMUs in [dataDir].
         */
        fun parse(text: String, dataDir: String): FlycastStandaloneConfig {
            val entries = IniSections.entries(text, SECTION)
            val perGame = entries[PER_GAME_VMU]?.let { it.lowercase() in TRUE_VALUES } ?: true
            val vmuPath = entries[VMU_PATH].orEmpty()
            val vmuDir = if (vmuPath.isEmpty()) dataDir else pathOf(vmuPath)
            return FlycastStandaloneConfig(perGame, vmuDir?.trimEnd('/'))
        }

        /**
         * The file path a `Dreamcast.VMUPath` value names: a plain path as it is, or the folder an
         * external-storage tree URI points at. Null for any other URI.
         */
        fun pathOf(value: String): String? {
            if (value.startsWith("/")) return value
            if (!value.startsWith(TREE_PREFIX)) return null
            val treeId = URLDecoder.decode(value.removePrefix(TREE_PREFIX).substringBefore('/'), Charsets.UTF_8.name())
            val volume = treeId.substringBefore(':', missingDelimiterValue = "").ifEmpty { return null }
            val relative = treeId.substringAfter(':')
            val root = if (volume == PRIMARY_VOLUME) PRIMARY_ROOT else "/storage/$volume"
            return if (relative.isEmpty()) root else "$root/$relative"
        }
    }
}
