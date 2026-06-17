package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.data.local.entity.CoreInputMode

data class CoreControlDef(
    val retropadId: Int,
    val label: String,
    val mode: CoreInputMode = CoreInputMode.PULSE,
    val gatedByOption: String? = null
)

interface CoreControlManifest {
    val coreId: String
    val controls: List<CoreControlDef>
}
