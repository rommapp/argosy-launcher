package com.nendo.argosy.libretro

sealed class NetplayCoreResolution {
    data class Matched(val corePath: String) : NetplayCoreResolution()
    data class Updated(val corePath: String) : NetplayCoreResolution()
    data class CompatFound(val corePath: String) : NetplayCoreResolution()
    data object Unresolvable : NetplayCoreResolution()
}
