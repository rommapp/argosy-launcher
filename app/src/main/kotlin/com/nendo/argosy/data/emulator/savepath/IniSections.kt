package com.nendo.argosy.data.emulator.savepath

internal object IniSections {

    data class Entry(val section: String, val key: String, val value: String)

    /**
     * The entries of section [wanted] in [text], read as [allEntries] reads them. A key written
     * twice keeps its last value.
     */
    fun entries(text: String, wanted: String): Map<String, String> =
        allEntries(text).filter { it.section == wanted }.associate { it.key to it.value }

    /**
     * Every entry in [text] in file order, duplicates included. Lines starting with `;` are
     * comments, keys are trimmed of spaces and tabs, one space after `=` is dropped and a
     * double-quoted value loses its quotes. Entries before the first section header have an
     * empty section.
     */
    fun allEntries(text: String): List<Entry> {
        val entries = mutableListOf<Entry>()
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trimStart()
            if (line.isEmpty() || line.startsWith(";")) continue
            if (line.startsWith("[")) {
                section = line.substring(1).substringBefore(']', missingDelimiterValue = section)
                continue
            }
            val eq = line.indexOf('=').takeIf { it > 0 } ?: continue
            val key = line.substring(0, eq).trim(' ', '\t')
            entries += Entry(section, key, unquote(line.substring(eq + 1).removePrefix(" ")))
        }
        return entries
    }

    private fun unquote(value: String): String {
        if (!value.startsWith("\"")) return value
        val end = value.lastIndexOf('"').takeIf { it > 0 } ?: value.length
        return value.substring(1, end)
    }
}
