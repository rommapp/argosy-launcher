package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class SavePathForPackageTest {

    private val stable = "dev.eden.eden_emulator"
    private val nightly = "dev.eden.eden_emulator.nightly"

    private fun saves(pkg: String, root: String = "/storage/emulated/0") =
        "$root/Android/data/$pkg/files/nand/user/save"

    @Test
    fun `a path in one Eden build's folder moves to the other build's folder`() {
        assertEquals(saves(nightly), SavePathRegistry.pathForPackage(saves(stable), nightly))
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(nightly), stable))
    }

    @Test
    fun `the move keeps the storage volume`() {
        assertEquals(
            saves(nightly, "/storage/546B-6466"),
            SavePathRegistry.pathForPackage(saves(stable, "/storage/546B-6466"), nightly)
        )
    }

    @Test
    fun `the build's own folder and an unknown build leave the path alone`() {
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(stable), stable))
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(stable), null))
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(stable), ""))
    }

    @Test
    fun `a folder outside any app's data stays where the user put it`() {
        assertEquals("/sd/switch-saves", SavePathRegistry.pathForPackage("/sd/switch-saves", nightly))
    }

    @Test
    fun `a build with its own save layout is never mixed with another`() {
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(stable), "dev.eden.nx2"))
        assertEquals(saves(stable), SavePathRegistry.pathForPackage(saves(stable), "org.citron.citron_emu"))
        assertEquals(saves("org.citron.citron_emu"), SavePathRegistry.pathForPackage(saves("org.citron.citron_emu"), nightly))
    }
}
