package com.nendo.argosy.data.netplay

import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid

interface LibretroNetplayOps {
    fun setInputPortState(port: Int, bitmask: Int)
    fun stepForNetplay(retroView: GLRetroView)
}

object RealLibretroNetplayOps : LibretroNetplayOps {
    override fun setInputPortState(port: Int, bitmask: Int) {
        LibretroDroid.setInputPortState(port, bitmask)
    }

    override fun stepForNetplay(retroView: GLRetroView) {
        LibretroDroid.stepForNetplay(retroView)
    }
}
