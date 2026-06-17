package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.data.repository.RetroButton

private fun control(retropadId: Int, label: String) = CoreControlDef(retropadId, label)

private fun manifest(id: String, vararg controls: CoreControlDef) =
    object : CoreControlManifest {
        override val coreId = id
        override val controls = controls.toList()
    }

object CoreControlManifestRegistry {

    private val manifests: Map<String, CoreControlManifest> = buildMap {
        fun add(m: CoreControlManifest) = put(m.coreId, m)

        add(manifest("melonds", control(RetroButton.R2, "Swap screens")))
        add(manifest("handy", control(RetroButton.SELECT, "Rotate screen")))
        add(manifest("mednafen_lynx", control(RetroButton.SELECT, "Rotate screen")))
        add(manifest("mednafen_wswan", control(RetroButton.SELECT, "Rotate screen")))
        add(manifest("mednafen_vb", control(RetroButton.X, "Low-battery toggle")))
        add(
            manifest(
                "freeintv",
                control(RetroButton.START, "Pause"),
                control(RetroButton.SELECT, "Swap controllers")
            )
        )
        add(
            manifest(
                "freechaf",
                control(RetroButton.START, "Console input mode"),
                control(RetroButton.SELECT, "Swap controllers")
            )
        )
        add(
            manifest(
                "o2em",
                control(RetroButton.SELECT, "Virtual keyboard"),
                control(RetroButton.Y, "Move keyboard")
            )
        )
        add(manifest("fuse", control(RetroButton.SELECT, "Keyboard overlay")))
        add(manifest("np2kai", control(RetroButton.A, "System menu")))
        add(
            manifest(
                "fceumm",
                control(RetroButton.L, "FDS disk side"),
                control(RetroButton.R, "FDS insert/eject")
            )
        )
        add(
            manifest(
                "nestopia",
                control(RetroButton.L, "FDS disk side"),
                control(RetroButton.R, "FDS eject")
            )
        )
        add(
            manifest(
                "mgba",
                control(RetroButton.R3, "Solar sensor brighten"),
                control(RetroButton.L3, "Solar sensor darken")
            )
        )
        add(
            manifest(
                "vbam",
                control(RetroButton.R2, "Solar sensor lighter"),
                control(RetroButton.L2, "Solar sensor darker")
            )
        )
        add(
            manifest(
                "gambatte",
                control(RetroButton.L, "Previous palette"),
                control(RetroButton.R, "Next palette")
            )
        )
        add(
            manifest(
                "picodrive",
                control(RetroButton.L, "Previous page"),
                control(RetroButton.R, "Next page")
            )
        )
    }

    fun getManifest(coreId: String): CoreControlManifest? = manifests[coreId]

    fun hasManifest(coreId: String): Boolean = coreId in manifests
}
