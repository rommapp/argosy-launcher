package com.nendo.argosy.data.emulator.config

internal object ConfigFixtures {
    fun text(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("emulator-config/$name")) { "missing fixture $name" }.readText()
}
