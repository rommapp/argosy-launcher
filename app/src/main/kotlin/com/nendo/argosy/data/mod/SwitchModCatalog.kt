package com.nendo.argosy.data.mod

internal object SwitchModCatalog {
    fun merge(
        downloaded: List<DownloadedSwitchMod>,
        stored: List<StoredModPackage>,
        root: String,
        title: String
    ): List<DownloadedSwitchMod> {
        val known = downloaded.map { it.titleId to it.packageId }.toSet()
        val orphans = stored.mapNotNull { record ->
            val metadata = record.pending ?: record.installed ?: return@mapNotNull null
            if (metadata.dataRoot != root || (record.pending == null && metadata.titleId != title) ||
                (metadata.titleId to metadata.packageId) in known) return@mapNotNull null
            DownloadedSwitchMod(
                titleId = metadata.titleId,
                packageId = metadata.packageId,
                fileName = "${metadata.titleId} · ${metadata.packageId}",
                archivePath = null,
                installed = record.installed != null,
                needsRecovery = record.pending != null,
                hasHandheldProfile = false
            )
        }
        return (downloaded + orphans).sortedWith(compareByDescending<DownloadedSwitchMod> { it.needsRecovery }.thenBy { it.fileName })
    }
}
