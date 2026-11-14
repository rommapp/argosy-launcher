package com.nendo.argosy.data.mod

import java.io.File
import java.nio.file.Files

internal object SwitchModLaunchGate {
    fun stateRoot(appFilesDir: File): File = File(appFilesDir, "switch-mod-installs")

    fun isBlocked(appFilesDir: File): Boolean {
        if (SwitchModInstaller.isBusy) return true
        val root = stateRoot(appFilesDir)
        if (!root.exists()) return false
        if (Files.isSymbolicLink(root.toPath())) return true
        val packages = root.listFiles() ?: return true
        return packages.any { File(it, "transaction/pending.properties").exists() }
    }
}
