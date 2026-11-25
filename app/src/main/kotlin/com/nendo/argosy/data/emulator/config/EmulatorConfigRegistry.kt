package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.EmulatorRegistry

object EmulatorConfigRegistry {

    internal val specs: Map<String, EmulatorConfigSpec> = mapOf(
        "eden" to EdenConfigSpec.SPEC,
        "dolphin" to DolphinConfigSpec.SPEC,
        "duckstation" to DuckStationConfigSpec.SPEC,
        "armsx2_refresh" to Armsx2ConfigSpec.SPEC,
        "vita3k" to Vita3kConfigSpec.SPEC,
        "vita3k-zx" to Vita3kConfigSpec.SPEC,
        "vita3k-plus" to Vita3kConfigSpec.SPEC,
        "cemu" to CemuConfigSpec.SPEC,
        "cemu_dualscreen" to CemuConfigSpec.SPEC
    )

    /**
     * The config spec for [emulatorId], looked up by the id itself and then by the id of the
     * family it is a variant of. Null for an emulator whose config Argosy does not read.
     */
    fun specFor(emulatorId: String): EmulatorConfigSpec? =
        specs[emulatorId] ?: specs[EmulatorRegistry.familyBaseIdFor(emulatorId)]
}
