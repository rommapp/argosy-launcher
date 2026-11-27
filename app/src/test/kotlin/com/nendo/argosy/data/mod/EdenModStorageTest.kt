package com.nendo.argosy.data.mod

import org.junit.Assert.assertEquals
import org.junit.Test

class EdenModStorageTest {
    private val root = "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files"

    @Test
    fun `missing empty and default storage paths remain supported`() {
        listOf("", "[Data%20Storage]\nload_directory=\nsdmc_directory=\n[UI]\nPaths\\sdmc_directory=",
            "[Data%20Storage]\nload_directory=$root/load/\nsdmc_directory=$root/sdmc\n[UI]\nPaths\\sdmc_directory=\"$root/sdmc/\"").forEach {
            EdenModStorage.requireDefaults(it, root)
        }
    }

    @Test
    fun `rejects the Android UI path override with absent or default data storage`() {
        listOf("", "[Data%20Storage]\nsdmc_directory=$root/sdmc\n").forEach { data ->
            reject("${data}[UI]\nPaths\\sdmc_directory=/custom/sdmc")
        }
    }

    @Test
    fun `rejects data storage load and SDMC overrides`() {
        listOf("load_directory", "sdmc_directory").forEach { key ->
            reject("[Data%20Storage]\n$key=/custom/location")
        }
    }

    @Test
    fun `Android UI SDMC takes precedence regardless of section order`() {
        val data = "[Data%20Storage]\nsdmc_directory=/custom/sdmc\n"
        val ui = "[UI]\nPaths\\sdmc_directory=$root/sdmc\n"
        EdenModStorage.requireDefaults(data + ui, root)
        EdenModStorage.requireDefaults(ui + data, root)
        reject("[Data%20Storage]\nsdmc_directory=/custom/sdmc\n[UI]\nPaths\\sdmc_directory=")
    }

    @Test
    fun `UI SDMC does not override the load path and default flags do not suppress active paths`() {
        reject("[Data%20Storage]\nload_directory=/custom/load\n[UI]\nPaths\\sdmc_directory=$root/sdmc")
        reject("[UI]\nPaths\\sdmc_directory=/custom/sdmc\nPaths\\sdmc_directory\\default=true")
    }

    @Test
    fun `recognizes case insensitive serialized keys and section comments`() {
        reject("[ui] ; Android paths\npAtHs\\SDMC_directory=/custom/sdmc")
        EdenModStorage.requireDefaults("[UI]\nPaths\\sdmc_directory=\"$root\"/sdmc", root)
    }

    private fun reject(config: String) {
        try {
            EdenModStorage.requireDefaults(config, root)
            throw AssertionError("Expected custom storage rejection")
        } catch (failure: ModInstallException) {
            assertEquals(ModInstallFailure.UNSUPPORTED_PATH, failure.reason)
        }
    }
}
