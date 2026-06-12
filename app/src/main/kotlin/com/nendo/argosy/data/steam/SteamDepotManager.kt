package com.nendo.argosy.data.steam

import android.util.Log
import `in`.dragonbra.javasteam.steam.cdn.Client as CDNClient
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.DepotKeyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.future.await
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SteamDepotManager"
private const val WINDOWS_OS = "windows"
private const val ARCH_64 = "64"

@Singleton
class SteamDepotManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var steamClient: SteamClient? = null
    private var steamApps: SteamApps? = null
    private var steamContent: SteamContent? = null
    private var callbackManager: CallbackManager? = null
    private val appInfoCache = ConcurrentHashMap<Int, KeyValue>()

    fun initialize(client: SteamClient, apps: SteamApps, content: SteamContent, cm: CallbackManager) {
        steamClient = client
        steamApps = apps
        steamContent = content
        callbackManager = cm
    }

    fun clearHandlers() {
        steamClient = null
        steamApps = null
        steamContent = null
        callbackManager = null
    }

    fun isConnected(): Boolean {
        return steamApps != null && callbackManager != null
    }

    fun getWindowsDepots(appInfo: KeyValue): List<DepotInfo> {
        val depots = mutableListOf<DepotInfo>()
        val depotsKv = appInfo["depots"] ?: return emptyList()

        for (child in depotsKv.children) {
            val depotIdStr = child.name ?: continue
            val depotId = depotIdStr.toIntOrNull() ?: continue

            if (depotId < 1000) continue

            val config = child["config"]
            val oslist = config["oslist"]?.asString()?.lowercase()
            val osarch = config["osarch"]?.asString()

            if (oslist != null && !oslist.contains(WINDOWS_OS)) {
                Log.v(TAG, "Skipping depot $depotId: OS=$oslist (not Windows)")
                continue
            }

            if (osarch != null && osarch != ARCH_64 && osarch != "32") {
                Log.v(TAG, "Skipping depot $depotId: arch=$osarch")
                continue
            }

            val manifests = child["manifests"]
            val publicManifest = manifests?.get("public")

            var manifestId = publicManifest?.asString()?.toLongOrNull()
                ?: publicManifest?.value?.toString()?.toLongOrNull()
                ?: 0L

            if (manifestId == 0L) {
                manifestId = publicManifest?.get("gid")?.asString()?.toLongOrNull() ?: 0L
            }

            if (manifestId == 0L) {
                Log.v(TAG, "Depot $depotId manifests children: ${manifests.children.map { "${it.name}=${it.value}" }}")
                if (publicManifest != null) {
                    Log.v(TAG, "Depot $depotId public manifest: value=${publicManifest.value}, children=${publicManifest.children.map { "${it.name}=${it.value}" }}")
                }
            }

            if (manifestId == 0L) {
                Log.v(TAG, "Skipping depot $depotId: no manifest")
                continue
            }

            val name = child["name"]?.asString() ?: "Depot $depotId"
            val maxSize = child["maxsize"]?.asString()?.toLongOrNull() ?: 0L

            depots.add(
                DepotInfo(
                    depotId = depotId,
                    manifestId = manifestId,
                    name = name,
                    os = oslist,
                    arch = osarch,
                    size = maxSize
                )
            )

            Log.v(TAG, "Found depot $depotId: $name (os=$oslist, arch=$osarch, manifest=$manifestId, size=${maxSize / 1024 / 1024}MB)")
        }

        Log.d(TAG, "Found ${depots.size} Windows depots total")
        return depots
    }

    data class DepotSizeResult(
        val totalSize: Long,
        val accessibleDepotIds: List<Int>,
        val depotSizes: Map<Int, Long> = emptyMap(),
        val fileSizes: Map<Pair<Int, String>, Long> = emptyMap()
    )

    suspend fun fetchDepotSizes(appId: Int, depots: List<DepotInfo>): DepotSizeResult = withContext(Dispatchers.IO) {
        val client = steamClient ?: return@withContext DepotSizeResult(0L, emptyList())
        val content = steamContent ?: return@withContext DepotSizeResult(0L, emptyList())
        val apps = steamApps ?: return@withContext DepotSizeResult(0L, emptyList())
        val cm = callbackManager ?: return@withContext DepotSizeResult(0L, emptyList())

        Log.d(TAG, "Fetching depot sizes for ${depots.size} depots (max 4 concurrent)")

        val servers = content.getServersForSteamPipe(null, null, scope).await()
        if (servers.isEmpty()) return@withContext DepotSizeResult(0L, emptyList())

        val cdnClient = CDNClient(client)
        val semaphore = Semaphore(4)
        val results = depots.filter { it.manifestId != 0L }.map { depot ->
            scope.async {
                semaphore.withPermit {
                    try {
                        val depotKey = getDepotKey(apps, cm, depot.depotId, appId) ?: return@withPermit null
                        val requestCode = content.getManifestRequestCode(
                            depot.depotId, appId, depot.manifestId, "public", null, scope
                        ).await()
                        if (requestCode.toLong() == 0L) return@withPermit null

                        val server = servers.random()
                        val authToken = content.getCDNAuthToken(appId, depot.depotId, server.host ?: "", scope).await()
                        val manifest = cdnClient.downloadManifestFuture(
                            depot.depotId, depot.manifestId, requestCode.toLong(), server, depotKey, null, authToken.token
                        ).await()

                        val size = manifest.totalUncompressedSize
                        val fileEntries = manifest.files
                            ?.filter {
                                !it.fileName.isNullOrBlank() &&
                                    it.flags?.contains(EDepotFileFlag.Directory) != true &&
                                    it.flags?.contains(EDepotFileFlag.Symlink) != true
                            }
                            ?.associate { (depot.depotId to it.fileName.replace('\\', '/')) to it.totalSize }
                            ?: emptyMap()
                        Log.d(TAG, "Depot ${depot.depotId}: ${size / 1024 / 1024}MB, ${fileEntries.size} files indexed")
                        Triple(depot.depotId, size, fileEntries)
                    } catch (e: Exception) {
                        Log.w(TAG, "Depot ${depot.depotId} size fetch failed: ${e.message}")
                        null
                    }
                }
            }
        }

        val completed = results.mapNotNull { it.await() }
        val depotSizes = completed.associate { it.first to it.second }
        val fileSizes = completed.fold(mutableMapOf<Pair<Int, String>, Long>()) { acc, (_, _, files) ->
            acc.apply { putAll(files) }
        }
        val totalSize = completed.sumOf { it.second }
        val accessible = completed.map { it.first }

        Log.d(TAG, "Total: ${totalSize / 1024 / 1024}MB (${accessible.size}/${depots.size} depots), ${fileSizes.size} files indexed")
        DepotSizeResult(totalSize, accessible, depotSizes, fileSizes)
    }

    suspend fun fetchAppInfo(appId: Int): KeyValue {
        appInfoCache[appId]?.let {
            Log.d(TAG, "AppInfo cache hit for app $appId")
            return it
        }

        return withTimeout(30_000L) {
            if (!isConnected()) {
                throw IllegalStateException("Steam not connected")
            }

            suspendCancellableCoroutine { continuation ->
                val apps = steamApps
                val cm = callbackManager

                if (apps == null || cm == null) {
                    continuation.resumeWithException(IllegalStateException("Steam not connected"))
                    return@suspendCancellableCoroutine
                }

                Log.d(TAG, "Fetching PICS info for app $appId")

                var subscription: Closeable? = null
                subscription = cm.subscribe(PICSProductInfoCallback::class.java) { callback ->
                    val appInfo = callback.apps[appId]
                    if (appInfo != null) {
                        Log.d(TAG, "Received PICS info for app $appId")
                        subscription?.close()
                        appInfoCache[appId] = appInfo.keyValues
                        continuation.resume(appInfo.keyValues)
                    }
                }

                continuation.invokeOnCancellation {
                    subscription.close()
                }

                try {
                    apps.picsGetProductInfo(PICSRequest(appId), null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request PICS info for app $appId", e)
                    subscription.close()
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    fun checkForUpdate(appInfo: KeyValue, currentBuildId: String?): Boolean {
        val publicBranch = appInfo["depots"]?.get("branches")?.get("public")
        val latestBuildId = publicBranch?.get("buildid")?.asString()
        return latestBuildId != null && currentBuildId != null && latestBuildId != currentBuildId
    }

    fun getEstimatedSize(appInfo: KeyValue): Long {
        val depots = getWindowsDepots(appInfo)
        return depots.sumOf { it.size }
    }

    suspend fun getDepotKey(apps: SteamApps, cm: CallbackManager, depotId: Int, appId: Int): ByteArray? {
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                var subscription: Closeable? = null
                subscription = cm.subscribe(DepotKeyCallback::class.java) { callback ->
                    if (callback.depotID == depotId) {
                        subscription?.close()
                        if (callback.result == `in`.dragonbra.javasteam.enums.EResult.OK) {
                            continuation.resume(callback.depotKey)
                        } else {
                            continuation.resume(null)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    subscription.close()
                }

                apps.getDepotDecryptionKey(depotId, appId)
            }
        }
    }
}
