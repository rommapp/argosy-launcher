package com.nendo.argosy.libretro.coreoptions

data class CoreOptionDef(
    val key: String,
    val displayName: String,
    val values: List<String>,
    val defaultValue: String,
    val coreDefault: String = defaultValue,
    val description: String? = null
)
