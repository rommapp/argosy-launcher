package com.nendo.argosy.data.cache

private val SWEPT_ART_PREFIXES = listOf("cover_", "bg_", "steam_bg_", "game_logo_", "ss_")
private val KEPT_ART_PREFIXES = listOf(
    "cover_override_", "cover_manual_", "bg_override_", "bg_custom_", "logo_override_"
)

data class ArtCacheFile(val path: String, val name: String, val lastModified: Long)

data class ArtSweepPlan(
    val orphanFiles: List<String>,
    val missingCachedPaths: Set<String>,
    val missingOverridePaths: Set<String>
)

/**
 * Whether [name] is a file the image cache writes for a `game_art` or `game_screenshots` cached
 * path. Override files, box faces, user screenshots, badges and app icons never qualify.
 */
fun isSweptArtFileName(name: String): Boolean =
    SWEPT_ART_PREFIXES.any { name.startsWith(it) } && KEPT_ART_PREFIXES.none { name.startsWith(it) }

/**
 * Set difference between the art files listed on disk and the stored art paths. A swept art file
 * no cached or override path references is an orphan unless it was modified at or after
 * [recentCutoff]. A local path not among [listedFiles] is reported missing; callers confirm a
 * missing path against the storage volume before clearing it.
 */
fun planArtSweep(
    listedFiles: Collection<ArtCacheFile>,
    cachedPaths: Collection<String>,
    overridePaths: Collection<String>,
    recentCutoff: Long
): ArtSweepPlan {
    val onDisk = listedFiles.mapTo(HashSet(listedFiles.size)) { it.path }
    val referenced = HashSet<String>(cachedPaths.size + overridePaths.size).apply {
        addAll(cachedPaths)
        addAll(overridePaths)
    }
    val orphans = listedFiles
        .filter { isSweptArtFileName(it.name) && it.path !in referenced && it.lastModified < recentCutoff }
        .map { it.path }
    val isMissing: (String) -> Boolean = { path -> path.startsWith("/") && path !in onDisk }
    return ArtSweepPlan(
        orphanFiles = orphans,
        missingCachedPaths = cachedPaths.filterTo(HashSet(), isMissing),
        missingOverridePaths = overridePaths.filterTo(HashSet(), isMissing)
    )
}
