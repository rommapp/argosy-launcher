package com.nendo.argosy.libretro.coreoptions

data class CoreOptionDef(
    val key: String,
    val displayName: String,
    val values: List<String>,
    val defaultValue: String,
    val coreDefault: String = defaultValue,
    val description: String? = null,
    val valueLabels: Map<String, String> = emptyMap()
) {
    fun displayValueFor(value: String): String = valueLabels[value] ?: value
}
