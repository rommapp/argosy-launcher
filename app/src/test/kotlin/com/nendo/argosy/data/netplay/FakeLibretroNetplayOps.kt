package com.nendo.argosy.data.netplay

import com.swordfish.libretrodroid.GLRetroView

internal class FakeLibretroNetplayOps : LibretroNetplayOps {
    val setCalls = mutableListOf<Pair<Int, Int>>()
    var stepCount: Int = 0

    override fun setInputPortState(port: Int, bitmask: Int) {
        setCalls += (port to bitmask)
    }

    override fun stepForNetplay(retroView: GLRetroView) {
        stepCount += 1
    }
}
