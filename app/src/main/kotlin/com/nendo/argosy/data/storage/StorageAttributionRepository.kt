package com.nendo.argosy.data.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

private const val DATABASE_NAME = "alauncher.db"
private const val TMP_SUFFIX = ".tmp"
private const val WALK_PROGRESS_INTERVAL = 512
private const val WALK_PROGRESS_FILES = 512
private const val GAMES_PROGRESS_INTERVAL = 128
private const val CONCURRENT_WALKS = 3
private const val APPS_PROGRESS_INTERVAL = 32
private const val INTERNAL_DRIFT_EPSILON = 256L * 1024 * 1024
private const val EXTERNAL_DRIFT_EPSILON = 64L * 1024 * 1024

/** Recomputed on every page open: quota/stat lookups, no filesystem walking. */
private val CHEAP_CATEGORIES = setOf(
    StorageCategory.DATABASE,
    StorageCategory.ANDROID_APPS
)

/** PC game stores whose app data (game installs included) attributes to STEAM instead of GAMES. */
private val PC_STORE_PACKAGES = setOf("app.gamenative")

private val NIO_CATEGORIES = setOf(
    StorageCategory.MUSIC,
    StorageCategory.IMAGE_CACHE
)

/** Computes per-category, per-volume disk usage attribution; walks run on an app-lifetime IO scope. */
@Singleton
class StorageAttributionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val volumeDetector: StorageVolumeDetector,
    private val snapshotStore: StorageSnapshotStore,
    private val storagePreferences: StoragePreferencesRepository,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val imageCacheManager: ImageCacheManager,
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao,
    private val platformDao: PlatformDao,
    private val permissionHelper: PermissionHelper
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "StorageAttribution")
    private val refreshLock = Any()
    private val refreshMutex = Mutex()
    private var activeRefresh: Job? = null

    private val _snapshot = MutableStateFlow<StorageSnapshot?>(null)
    val snapshot: StateFlow<StorageSnapshot?> = _snapshot.asStateFlow()

    private val _walkProgress = MutableStateFlow<Map<StorageCategory, WalkState>>(emptyMap())
    val walkProgress: StateFlow<Map<StorageCategory, WalkState>> = _walkProgress.asStateFlow()

    private val _volumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val volumes: StateFlow<List<StorageVolumeInfo>> = _volumes.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _dirtyCategories = MutableStateFlow<Set<StorageCategory>>(emptySet())
    val dirtyCategories: StateFlow<Set<StorageCategory>> = _dirtyCategories.asStateFlow()

    private val initialLoad: Job = scope.launch {
        snapshotStore.load()?.let { persisted ->
            _snapshot.update { current -> current ?: persisted }
        }
    }

    fun markDirty(category: StorageCategory) {
        _dirtyCategories.update { it + category }
    }

    /**
     * Single-flight full recompute; [force] cancels an active refresh. Clean-fingerprint volumes
     * still reuse their store-walk totals unless [deep], which re-walks everything.
     */
    fun refresh(force: Boolean = false, deep: Boolean = false): Job = synchronized(refreshLock) {
        val current = activeRefresh
        if (current != null && current.isActive) {
            if (!force) return current
            current.cancel()
        }
        scope.launch {
            refreshMutex.withLock {
                initialLoad.join()
                val reusable = if (deep) {
                    emptySet()
                } else {
                    val volumes = detectVolumes()
                    volumes.map { it.key }.toSet() - driftedVolumeKeys(_snapshot.value, volumes)
                }
                runRefresh(StorageCategory.entries.toSet(), reusableVolumeKeys = reusable)
            }
        }.also { activeRefresh = it }
    }

    /**
     * Page-open validation: re-detects volumes and recomputes cheap categories every time, but
     * walks only categories that are dirty, missing, or rooted on a volume whose fingerprint drifted.
     */
    fun refreshOnOpen(): Job = synchronized(refreshLock) {
        val current = activeRefresh
        if (current != null && current.isActive) return current
        scope.launch {
            initialLoad.join()
            val volumes = detectVolumes()
            _volumes.value = volumes
            val snap = _snapshot.value
            val drifted = driftedVolumeKeys(snap, volumes)
            val stale = staleCategories(snap, volumes, drifted)
            if (stale.isNotEmpty()) {
                refreshMutex.withLock {
                    runRefresh(stale, reusableVolumeKeys = volumes.map { it.key }.toSet() - drifted)
                }
            }
        }.also { activeRefresh = it }
    }

    private fun driftedVolumeKeys(
        snap: StorageSnapshot?,
        volumes: List<StorageVolumeInfo>
    ): Set<String> {
        if (snap == null) return volumes.map { it.key }.toSet()
        val drifted = volumes.filter { volume ->
            val fingerprint = snap.volumeFingerprints[volume.key] ?: return@filter true
            val used = volume.totalBytes - volume.availableBytes
            val epsilon = if (volume.type == StorageVolumeType.INTERNAL) {
                INTERNAL_DRIFT_EPSILON
            } else {
                EXTERNAL_DRIFT_EPSILON
            }
            fingerprint.totalBytes != volume.totalBytes || abs(used - fingerprint.usedBytes) > epsilon
        }.map { it.key }.toMutableSet()
        drifted += snap.volumeFingerprints.keys - volumes.map { it.key }.toSet()
        return drifted
    }

    private suspend fun staleCategories(
        snap: StorageSnapshot?,
        volumes: List<StorageVolumeInfo>,
        driftedKeys: Set<String>
    ): Set<StorageCategory> {
        if (snap == null) return StorageCategory.entries.toSet()
        val result = mutableSetOf<StorageCategory>()
        result += CHEAP_CATEGORIES
        result += _dirtyCategories.value
        result += StorageCategory.entries.filter { it !in snap.categories }
        if (driftedKeys.isNotEmpty()) {
            result += StorageCategory.GAMES
            result += StorageCategory.STEAM
            val internalKey = volumes.firstOrNull { it.type == StorageVolumeType.INTERNAL }?.key
                ?: volumes.firstOrNull()?.key
            val musicDir = musicDirectoryManager.resolveMusicDir()
            for ((category, roots) in resolveCategoryRoots(musicDir)) {
                val rootedOnDrifted = roots.any { root ->
                    val key = volumeKeyFor(StoragePathUtils.canonicalize(root.absolutePath), volumes, internalKey)
                    key in driftedKeys
                }
                if (rootedOnDrifted) result += category
            }
            result += snap.categories.filterValues { usage ->
                usage.perVolume.keys.any { it in driftedKeys }
            }.keys
        }
        return result
    }

    private suspend fun runRefresh(categories: Set<StorageCategory>, reusableVolumeKeys: Set<String>) {
        val ctx = currentCoroutineContext()
        _isRefreshing.value = true
        try {
            val volumes = detectVolumes()
            _volumes.value = volumes
            val internalKey = volumes.firstOrNull { it.type == StorageVolumeType.INTERNAL }?.key
                ?: volumes.firstOrNull()?.key
            _walkProgress.value = categories.associateWith { WalkState.Pending }

            val musicDir = musicDirectoryManager.resolveMusicDir()
            val musicRoot = StoragePathUtils.canonicalize(musicDir.absolutePath)

            if (StorageCategory.GAMES in categories) {
                setWalkState(StorageCategory.GAMES, WalkState.Walking(0L, 0))
                try {
                    val games = collectGames(ctx, volumes, internalKey, musicRoot)
                    publishCategory(StorageCategory.GAMES, games.usage, games.perPlatform)
                    setWalkState(StorageCategory.GAMES, WalkState.Complete)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    setWalkState(StorageCategory.GAMES, WalkState.Failed)
                    if (_snapshot.value?.categories?.containsKey(StorageCategory.GAMES) != true) {
                        publishCategory(StorageCategory.GAMES, CategoryUsage())
                    }
                }
            }

            if (StorageCategory.DATABASE in categories) {
                runCategory(StorageCategory.DATABASE) { databaseUsage(volumes, internalKey) }
            }

            val walkSemaphore = Semaphore(CONCURRENT_WALKS)
            coroutineScope {
                resolveCategoryRoots(musicDir).filterKeys { it in categories }.map { (category, roots) ->
                    launch {
                        walkSemaphore.withPermit {
                            runCategory(category) {
                                walkRoots(category, roots, volumes, internalKey, ctx, useNio = category in NIO_CATEGORIES)
                            }
                        }
                    }
                }
                if (StorageCategory.STEAM in categories) {
                    launch {
                        walkSemaphore.withPermit {
                            runCategory(StorageCategory.STEAM) {
                                collectSteamPc(volumes, internalKey, ctx, reusableVolumeKeys)
                            }
                        }
                    }
                }
                if (StorageCategory.ANDROID_APPS in categories) {
                    launch {
                        runCategory(StorageCategory.ANDROID_APPS) { collectAndroidApps(volumes, ctx) }
                    }
                }
            }

            val finalVolumes = detectVolumes()
            _volumes.value = finalVolumes
            _snapshot.update { current ->
                val existing = current?.volumeFingerprints ?: emptyMap()
                current?.copy(
                    volumeFingerprints = finalVolumes.associate { volume ->
                        val kept = existing[volume.key]?.takeIf { volume.key in reusableVolumeKeys }
                        volume.key to (kept ?: VolumeFingerprint(
                            totalBytes = volume.totalBytes,
                            usedBytes = volume.totalBytes - volume.availableBytes
                        ))
                    }
                )
            }
            _snapshot.value?.let { snapshotStore.save(it) }
            _dirtyCategories.update { it - categories }
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun runCategory(category: StorageCategory, block: suspend () -> CategoryUsage) {
        setWalkState(category, WalkState.Walking(0L, 0))
        try {
            publishCategory(category, block())
            setWalkState(category, WalkState.Complete)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            setWalkState(category, WalkState.Failed)
            if (_snapshot.value?.categories?.containsKey(category) != true) {
                publishCategory(category, CategoryUsage())
            }
        }
    }

    private fun publishCategory(
        category: StorageCategory,
        usage: CategoryUsage,
        perPlatform: List<PlatformUsage>? = null
    ) {
        _snapshot.update { current ->
            val base = current ?: StorageSnapshot(0L, emptyMap(), emptyList())
            base.copy(
                computedAt = System.currentTimeMillis(),
                categories = base.categories + (category to usage),
                gamesPerPlatform = perPlatform ?: base.gamesPerPlatform
            )
        }
    }

    private fun setWalkState(category: StorageCategory, state: WalkState) {
        _walkProgress.update { it + (category to state) }
    }

    private fun detectVolumes(): List<StorageVolumeInfo> {
        val seenPaths = HashSet<String>()
        val seenFingerprints = HashSet<String>()
        val result = mutableListOf<StorageVolumeInfo>()
        for (volume in volumeDetector.detectStorageVolumes()) {
            if (volume.totalBytes <= 0L) continue
            val canonical = StoragePathUtils.canonicalize(volume.path).trimEnd('/')
            if (canonical.isEmpty()) continue
            if (!seenPaths.add(canonical)) continue
            val fingerprint = statFingerprint(canonical) ?: canonical
            if (!seenFingerprints.add(fingerprint)) continue
            result.add(
                StorageVolumeInfo(
                    key = canonical,
                    displayName = volume.displayName,
                    type = volume.type,
                    totalBytes = volume.totalBytes,
                    availableBytes = volume.availableBytes
                )
            )
        }
        return result
    }

    private fun statFingerprint(path: String): String? = try {
        val stat = StatFs(path)
        "${stat.blockSizeLong}:${stat.blockCountLong}:${stat.availableBlocksLong}"
    } catch (_: Exception) {
        null
    }

    private fun volumeKeyFor(
        canonicalPath: String,
        volumes: List<StorageVolumeInfo>,
        internalKey: String?
    ): String? {
        val match = volumes
            .filter { canonicalPath == it.key || canonicalPath.startsWith("${it.key}/") }
            .maxByOrNull { it.key.length }
        return match?.key ?: internalKey
    }

    private class UsageAccumulator {
        var bytes = 0L
        var files = 0
        val perVolume = HashMap<String, Long>()

        fun add(entryBytes: Long, entryFiles: Int, volumeKey: String?) {
            bytes += entryBytes
            files += entryFiles
            if (volumeKey != null && entryBytes > 0) perVolume.merge(volumeKey, entryBytes, Long::plus)
        }
    }

    private data class GamesResult(val usage: CategoryUsage, val perPlatform: List<PlatformUsage>)

    private suspend fun collectGames(
        ctx: CoroutineContext,
        volumes: List<StorageVolumeInfo>,
        internalKey: String?,
        musicRoot: String
    ): GamesResult {
        val gameRows = gameDao.getGamesWithLocalPathInfo()
        val fileRows = gameFileDao.getAllLocalPathsWithPlatform()
        val discRows = gameDiscDao.getAllLocalPathsWithPlatform()
        val platforms = platformDao.getAllPlatformsOrdered().associateBy { it.id }
        val storeRoots = pcStoreRoots(volumes)
        val downloadedCounts = gameRows.filter { row ->
            val path = row.localPath ?: return@filter true
            !isUnderAny(StoragePathUtils.canonicalize(path), storeRoots)
        }.groupingBy { it.platformId }.eachCount()

        val rows = ArrayList<Pair<Long, String>>(gameRows.size + fileRows.size + discRows.size)
        gameRows.forEach { row -> row.localPath?.let { rows.add(row.platformId to it) } }
        fileRows.forEach { rows.add(it.platformId to it.localPath) }
        discRows.forEach { rows.add(it.platformId to it.localPath) }

        val seen = HashSet<String>(rows.size * 2)
        val total = UsageAccumulator()
        val perPlatform = HashMap<Long, UsageAccumulator>()
        var processed = 0

        val directoryRows = ArrayList<Pair<Long, String>>()
        for ((platformId, rawPath) in rows) {
            ctx.ensureActive()
            val canonical = StoragePathUtils.canonicalize(rawPath)
            if (!seen.add(canonical)) continue
            if (canonical.endsWith(TMP_SUFFIX)) continue
            if (canonical == musicRoot || canonical.startsWith("$musicRoot/")) continue
            if (isUnderAny(canonical, storeRoots)) continue
            val entry = File(canonical)
            when {
                entry.isDirectory -> directoryRows.add(platformId to canonical)
                entry.isFile -> {
                    val volumeKey = volumeKeyFor(canonical, volumes, internalKey)
                    total.add(entry.length(), 1, volumeKey)
                    perPlatform.getOrPut(platformId) { UsageAccumulator() }
                        .add(entry.length(), 1, volumeKey)
                    processed += 1
                    if (processed % GAMES_PROGRESS_INTERVAL == 0) {
                        setWalkState(StorageCategory.GAMES, WalkState.Walking(total.bytes, total.files))
                    }
                }
            }
        }
        setWalkState(StorageCategory.GAMES, WalkState.Walking(total.bytes, total.files))

        for ((platformId, canonical) in directoryRows) {
            ctx.ensureActive()
            val measured = topDownWalk(File(canonical), ctx, excludeTmp = true) { walkBytes, walkFiles ->
                setWalkState(
                    StorageCategory.GAMES,
                    WalkState.Walking(total.bytes + walkBytes, total.files + walkFiles)
                )
            }
            val volumeKey = volumeKeyFor(canonical, volumes, internalKey)
            total.add(measured.first, measured.second, volumeKey)
            perPlatform.getOrPut(platformId) { UsageAccumulator() }
                .add(measured.first, measured.second, volumeKey)
            setWalkState(StorageCategory.GAMES, WalkState.Walking(total.bytes, total.files))
        }

        val perPlatformUsages = (perPlatform.keys + downloadedCounts.keys).map { platformId ->
            val meta = platforms[platformId]
            val acc = perPlatform[platformId]
            PlatformUsage(
                platformId = platformId,
                name = meta?.name ?: "Unknown",
                sortOrder = meta?.sortOrder ?: Int.MAX_VALUE,
                downloadedCount = downloadedCounts[platformId] ?: 0,
                bytes = acc?.bytes ?: 0L,
                perVolume = acc?.perVolume ?: emptyMap()
            )
        }.sortedBy { it.sortOrder }

        return GamesResult(
            usage = CategoryUsage(total.bytes, total.files, total.perVolume),
            perPlatform = perPlatformUsages
        )
    }

    private fun pcStoreRoots(volumes: List<StorageVolumeInfo>): List<String> =
        volumes.flatMap { volume -> PC_STORE_PACKAGES.map { "${volume.key}/Android/data/$it" } }

    private fun isUnderAny(canonicalPath: String, roots: List<String>): Boolean =
        roots.any { canonicalPath == it || canonicalPath.startsWith("$it/") }

    private fun collectSteamPc(
        volumes: List<StorageVolumeInfo>,
        internalKey: String?,
        ctx: CoroutineContext,
        reusableVolumeKeys: Set<String>
    ): CategoryUsage {
        val priorPerVolume = _snapshot.value?.categories?.get(StorageCategory.STEAM)?.perVolume
        val total = UsageAccumulator()
        val stagingRoots = listOf(
            AppPaths.steamStagingRoot(context.filesDir),
            File(context.filesDir, "steam_downloads")
        )
        for (root in stagingRoots) {
            if (!root.exists()) continue
            val volumeKey = volumeKeyFor(StoragePathUtils.canonicalize(root.absolutePath), volumes, internalKey)
            val measured = nioWalk(root, ctx) { bytes, files ->
                setWalkState(StorageCategory.STEAM, WalkState.Walking(total.bytes + bytes, total.files + files))
            }
            total.add(measured.first, measured.second, volumeKey)
        }
        for (volume in volumes) {
            val statsBytes = queryStoreStats(volume)
            val reusable = volume.key in reusableVolumeKeys && priorPerVolume?.containsKey(volume.key) == true
            when {
                statsBytes != null -> total.add(statsBytes, 0, volume.key)
                reusable -> total.add(priorPerVolume?.get(volume.key) ?: 0L, 0, volume.key)
                else -> {
                    for (pkg in PC_STORE_PACKAGES) {
                        val dir = File("${volume.key}/Android/data/$pkg")
                        if (!dir.isDirectory) continue
                        val measured = nioWalk(dir, ctx) { bytes, files ->
                            setWalkState(StorageCategory.STEAM, WalkState.Walking(total.bytes + bytes, total.files + files))
                        }
                        total.add(measured.first, measured.second, volume.key)
                    }
                }
            }
            setWalkState(StorageCategory.STEAM, WalkState.Walking(total.bytes, total.files))
        }
        return CategoryUsage(total.bytes, total.files, total.perVolume)
    }

    /** Store bytes for [volume] via quota stats, or null when the volume needs a filesystem walk. */
    private fun queryStoreStats(volume: StorageVolumeInfo): Long? {
        if (!permissionHelper.hasUsageStatsPermission(context)) return null
        val statsManager = context.getSystemService(StorageStatsManager::class.java) ?: return null
        val uuid = statsUuid(volume) ?: return null
        val user = Process.myUserHandle()
        var sum = 0L
        for (pkg in PC_STORE_PACKAGES) {
            val stats = try {
                statsManager.queryStatsForPackage(uuid, pkg, user)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            } catch (_: Exception) {
                return null
            }
            sum += stats.appBytes + stats.dataBytes
        }
        return sum
    }

    private fun collectAndroidApps(volumes: List<StorageVolumeInfo>, ctx: CoroutineContext): CategoryUsage {
        if (!permissionHelper.hasUsageStatsPermission(context)) return CategoryUsage()
        val statsManager = context.getSystemService(StorageStatsManager::class.java) ?: return CategoryUsage()
        val selfPrefix = context.packageName.removeSuffix(".debug")
        val apps = context.packageManager.getInstalledApplications(0).filter {
            !it.packageName.startsWith(selfPrefix) && it.packageName !in PC_STORE_PACKAGES
        }
        val user = Process.myUserHandle()
        val total = UsageAccumulator()
        var counted = 0
        for (volume in volumes) {
            val uuid = statsUuid(volume) ?: continue
            for (app in apps) {
                ctx.ensureActive()
                val stats = try {
                    statsManager.queryStatsForPackage(uuid, app.packageName, user)
                } catch (_: SecurityException) {
                    return CategoryUsage()
                } catch (_: Exception) {
                    continue
                }
                val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val isUpdatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                val appBytes = if (!isSystem || isUpdatedSystem) stats.appBytes else 0L
                val bytes = appBytes + stats.dataBytes
                if (bytes > 0L) counted += 1
                total.add(bytes, 0, volume.key)
                if (counted % APPS_PROGRESS_INTERVAL == 0) {
                    setWalkState(StorageCategory.ANDROID_APPS, WalkState.Walking(total.bytes, counted))
                }
            }
        }
        return CategoryUsage(total.bytes, counted, total.perVolume)
    }

    private fun statsUuid(volume: StorageVolumeInfo): UUID? = try {
        context.getSystemService(StorageManager::class.java)?.getUuidForPath(File(volume.key))
    } catch (_: Exception) {
        null
    }

    private suspend fun resolveCategoryRoots(musicDir: File): Map<StorageCategory, List<File>> {
        val prefs = storagePreferences.preferences.first()
        val filesDir = context.filesDir
        val cacheDir = context.cacheDir
        val externalFilesDir = context.getExternalFilesDir(null)
        val customBios = prefs.customBiosPath?.let { resolveCustomBiosDir(it) }
        return linkedMapOf(
            StorageCategory.SAVE_STATE_CACHE to listOf(
                AppPaths.saveCacheDir(filesDir),
                AppPaths.stateCacheDir(filesDir)
            ),
            StorageCategory.ROM_EXTRACTION to listOf(AppPaths.romCacheDir(filesDir)),
            StorageCategory.SFX_CACHE to listOf(File(cacheDir, "sfx")),
            StorageCategory.BIOS to listOfNotNull(
                File(filesDir, "bios"),
                AppPaths.libretroSystemDir(filesDir),
                customBios
            ),
            StorageCategory.CORES_SYSTEM to listOf(
                File(filesDir, "libretro/cores"),
                File(filesDir, "libretro/core_history"),
                File(filesDir, "libretro/compat_cores")
            ),
            StorageCategory.SHADERS_CATALOG to listOfNotNull(
                externalFilesDir?.let { File(it, "shaders/catalog") }
            ),
            StorageCategory.SHADERS_CUSTOM to listOfNotNull(
                externalFilesDir?.let { File(it, "shaders/custom") }
            ),
            StorageCategory.FRAMES to listOfNotNull(externalFilesDir?.let { File(it, "frames") }),
            StorageCategory.FONTS to listOf(File(filesDir, "fonts")),
            StorageCategory.EMULATOR_APKS to listOf(File(cacheDir, "emulator_apks")),
            StorageCategory.MISC_DOWNLOADS to listOf(
                File(cacheDir, "presence_covers"),
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "drivers"
                )
            ),
            StorageCategory.MUSIC to listOf(musicDir),
            StorageCategory.IMAGE_CACHE to listOf(
                File(imageCacheManager.getCurrentCachePath()),
                File(filesDir, "steam")
            )
        )
    }

    private fun resolveCustomBiosDir(basePath: String): File {
        val base = File(basePath)
        return if (base.name.equals("bios", ignoreCase = true)) base else File(base, "bios")
    }

    private fun walkRoots(
        category: StorageCategory,
        roots: List<File>,
        volumes: List<StorageVolumeInfo>,
        internalKey: String?,
        ctx: CoroutineContext,
        useNio: Boolean
    ): CategoryUsage {
        val total = UsageAccumulator()
        val seenRoots = HashSet<String>()
        for (root in roots) {
            val canonicalRoot = StoragePathUtils.canonicalize(root.absolutePath)
            if (!seenRoots.add(canonicalRoot)) continue
            if (!root.exists()) continue
            val volumeKey = volumeKeyFor(canonicalRoot, volumes, internalKey)
            val measured = if (useNio) {
                nioWalk(root, ctx) { bytes, files ->
                    setWalkState(category, WalkState.Walking(total.bytes + bytes, total.files + files))
                }
            } else {
                topDownWalk(root, ctx)
            }
            total.add(measured.first, measured.second, volumeKey)
            setWalkState(category, WalkState.Walking(total.bytes, total.files))
        }
        return CategoryUsage(total.bytes, total.files, total.perVolume)
    }

    private fun nioWalk(
        root: File,
        ctx: CoroutineContext,
        onProgress: (Long, Int) -> Unit
    ): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                ctx.ensureActive()
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
                if (attrs != null && attrs.isRegularFile) {
                    bytes += attrs.size()
                    files += 1
                    if (files % WALK_PROGRESS_INTERVAL == 0) onProgress(bytes, files)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult =
                FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult =
                FileVisitResult.CONTINUE
        })
        return bytes to files
    }

    private fun topDownWalk(
        root: File,
        ctx: CoroutineContext,
        excludeTmp: Boolean = false,
        onProgress: ((Long, Int) -> Unit)? = null
    ): Pair<Long, Int> {
        var bytes = 0L
        var files = 0
        root.walkTopDown()
            .onEnter {
                ctx.ensureActive()
                true
            }
            .forEach { entry ->
                if (entry.isFile && !(excludeTmp && entry.name.endsWith(TMP_SUFFIX))) {
                    bytes += entry.length()
                    files += 1
                    if (onProgress != null && files % WALK_PROGRESS_FILES == 0) {
                        onProgress(bytes, files)
                    }
                }
            }
        return bytes to files
    }

    private fun databaseUsage(volumes: List<StorageVolumeInfo>, internalKey: String?): CategoryUsage {
        val db = context.getDatabasePath(DATABASE_NAME)
        val parts = listOf(db, File(db.parentFile, "${db.name}-wal"), File(db.parentFile, "${db.name}-shm"))
        var bytes = 0L
        var count = 0
        parts.forEach { part ->
            if (part.isFile) {
                bytes += part.length()
                count += 1
            }
        }
        val volumeKey = volumeKeyFor(StoragePathUtils.canonicalize(db.absolutePath), volumes, internalKey)
        val perVolume = if (volumeKey != null && bytes > 0) mapOf(volumeKey to bytes) else emptyMap()
        return CategoryUsage(bytes, count, perVolume)
    }
}
