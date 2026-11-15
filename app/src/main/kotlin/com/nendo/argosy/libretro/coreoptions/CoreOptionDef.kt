package com.nendo.argosy.libretro.coreoptions

/**
 * An option the core reads only while the option [key] holds one of [values], such as a renderer's
 * own settings under the renderer it belongs to.
 */
data class CoreOptionRequirement(val key: String, val values: Set<String>)

data class CoreOptionDef(
    val key: String,
    val displayName: String,
    val values: List<String>,
    val defaultValue: String,
    val coreDefault: String = defaultValue,
    val description: String? = null,
    val valueLabels: Map<String, String> = emptyMap(),
    val legacyValues: Map<String, String> = emptyMap(),
    val requires: CoreOptionRequirement? = null
) {
    fun displayValueFor(value: String): String = valueLabels[value] ?: value

    /**
     * Whether the core reads this option given the values [valueOf] reports. An unknown value for
     * the required key counts as met, as the core shows every option when it cannot read its
     * renderer.
     */
    fun appliesWith(valueOf: (String) -> String?): Boolean {
        val requirement = requires ?: return true
        val current = valueOf(requirement.key) ?: return true
        return current in requirement.values
    }

    /**
     * A stored override can hold a token this option no longer offers, because the core renamed it
     * or because Argosy once shipped a token the core never accepted. A renamed token still carries
     * the user's choice and is repaired to its current spelling; anything else falls back to the
     * default rather than being sent to a core that would reject it.
     */
    fun resolveStored(stored: String): String =
        if (stored in values) stored else legacyValues[stored] ?: defaultValue
}
