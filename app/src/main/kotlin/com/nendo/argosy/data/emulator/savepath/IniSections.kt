package com.nendo.argosy.data.emulator.savepath

internal object IniSections {

    /**
     * The entries of section [wanted] in [text]. Lines starting with `;` are comments, keys are
     * trimmed of spaces and tabs, one space after `=` is dropped and a double-quoted value loses
     * its quotes.
     */
    fun entries(text: String, wanted: String): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        var section = ""
        for (raw in text.lineSequence()) {
            val line = raw.trimStart()
            if (line.isEmpty() || line.startsWith(";")) continue
            if (line.startsWith("[")) {
                section = line.substring(1).substringBefore(']', missingDelimiterValue = section)
                continue
            }
            val eq = line.indexOf('=').takeIf { it > 0 } ?: continue
            if (section != wanted) continue
            val key = line.substring(0, eq).trim(' ', '\t')
            entries[key] = unquote(line.substring(eq + 1).removePrefix(" "))
        }
        return entries
    }

    private fun unquote(value: String): String {
        if (!value.startsWith("\"")) return value
        val end = value.lastIndexOf('"').takeIf { it > 0 } ?: value.length
        return value.substring(1, end)
    }
}
