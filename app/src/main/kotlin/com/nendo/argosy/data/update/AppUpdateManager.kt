package com.nendo.argosy.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.model.AppUpdateDownload
import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Argosy's own updates: the check, the persisted result, skip and Later, the stored apk and the
 * hand-off to the system installer. File, network and preference work runs on [Dispatchers.IO].
 */
@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateRepository: UpdateRepository,
    private val appPreferences: AppPreferencesRepository,
    private val appInstaller: AppInstaller
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val store = UpdateStateStore(File(context.filesDir, STATE_FILE_NAME))
    private val files = UpdateApkFiles(File(context.filesDir, UPDATES_DIR_NAME))
    private val installedVersion: String = BuildConfig.VERSION_NAME
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val deferredTags = MutableStateFlow<Set<String>>(emptySet())
    private val forcedPrompt = MutableStateFlow<AppUpdateRelease?>(null)
    private var installJob: Job? = null

    val stored: StateFlow<StoredUpdateState> = store.state

    private val _download = MutableStateFlow<AppUpdateDownload>(AppUpdateDownload.Idle)
    val download: StateFlow<AppUpdateDownload> = _download.asStateFlow()

    val pendingPrompt: StateFlow<AppUpdateRelease?> = combine(
        store.state,
        deferredTags,
        appPreferences.preferences
            .map { UpdatePromptRules.checksAllowed(BuildConfig.DEBUG, it.debugUpdateChecksEnabled) }
            .distinctUntilChanged(),
        forcedPrompt
    ) { state, deferred, allowed, forced ->
        when {
            forced != null -> forced
            allowed -> UpdatePromptRules.promptFor(state.latest, state.skippedTag, deferred, installedVersion)
            else -> null
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { store.load() }
    }

    fun availableIn(state: StoredUpdateState): AppUpdateRelease? =
        UpdatePromptRules.available(state.latest, installedVersion)

    suspend fun isStored(release: AppUpdateRelease): Boolean =
        withContext(Dispatchers.IO) { files.isStored(release) }

    /**
     * Runs one check with the About page's rules and persists what it finds. Null when this build
     * does not check: a debug build with the developer option off.
     */
    suspend fun check(): UpdateState? = withContext(Dispatchers.IO) {
        val prefs = appPreferences.preferences.first()
        if (!UpdatePromptRules.checksAllowed(BuildConfig.DEBUG, prefs.debugUpdateChecksEnabled)) {
            Logger.debug(TAG, "Update check skipped: debug build with update checks off")
            return@withContext null
        }
        val result = updateRepository.checkForUpdates(prefs.betaUpdatesEnabled)
        when (result) {
            is UpdateState.UpdateAvailable -> store.update { it.copy(latest = result.toRelease()) }
            is UpdateState.UpToDate -> store.update { it.copy(latest = null) }
            else -> Unit
        }
        result
    }

    fun startupPass() {
        scope.launch {
            store.load()
            val removed = files.cleanup(installedVersion) + UpdateApkFiles.deleteLegacyCacheApks(context.cacheDir)
            if (removed.isNotEmpty()) {
                Logger.info(TAG, "Removed ${removed.size} stale update file(s): ${removed.joinToString { it.name }}")
            }
            awaitNetwork()
            when (val result = check()) {
                is UpdateState.UpdateAvailable -> Logger.info(TAG, "Startup check found ${result.release.tagName}")
                is UpdateState.Error -> Logger.info(TAG, "Startup check failed: ${result.message}")
                else -> Unit
            }
        }
    }

    /**
     * Opens the update prompt once for the newest release under the beta switch, newer than this
     * build or not, past a skipped or Later tag and the debug-build check switch. Nothing is
     * persisted. Returns the release lookup's result.
     */
    suspend fun showPromptNow(): UpdateState = withContext(Dispatchers.IO) {
        val prefs = appPreferences.preferences.first()
        val result = updateRepository.checkForUpdates(prefs.betaUpdatesEnabled, newerOnly = false)
        if (result is UpdateState.UpdateAvailable) forcedPrompt.value = result.toRelease()
        result
    }

    suspend fun clearSkippedTag() {
        withContext(Dispatchers.IO) { store.update { it.withoutSkippedTag() } }
    }

    fun skip(tag: String) {
        releaseForced(tag)
        scope.launch { store.update { it.copy(skippedTag = tag) } }
    }

    fun later(tag: String) {
        releaseForced(tag)
        deferredTags.update { it + tag }
    }

    private fun releaseForced(tag: String) {
        forcedPrompt.update { if (it?.tag == tag) null else it }
    }

    /**
     * Hands [release] to the system installer, downloading it first unless a complete copy is
     * stored. [fresh] deletes the stored copy and downloads it again.
     */
    fun install(release: AppUpdateRelease, fresh: Boolean = false) {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            try {
                val apk = obtain(release, fresh)
                store.update { if (it.skippedTag == release.tag) it.copy(skippedTag = null) else it }
                withContext(Dispatchers.Main) { appInstaller.installApk(context, apk) }
                deferredTags.update { it + release.tag }
                releaseForced(release.tag)
                _download.value = AppUpdateDownload.HandedOff(release.tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error(TAG, "Update ${release.tag} could not be installed", e)
                _download.value = AppUpdateDownload.Failed(release.tag, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun obtain(release: AppUpdateRelease, fresh: Boolean): File {
        if (fresh) {
            files.delete(release)
            Logger.info(TAG, "Deleted stored update ${release.tag} to download it again")
        }
        files.reusable(release)?.let { stored ->
            Logger.info(TAG, "Reusing stored update ${stored.name}, no download")
            return stored
        }
        return downloadToStore(release)
    }

    private fun downloadToStore(release: AppUpdateRelease): File {
        Logger.info(TAG, "Downloading update ${release.assetName}")
        _download.value = AppUpdateDownload.Downloading(release.tag, 0)
        val part = files.newPart(release)
        val request = Request.Builder().url(release.assetUrl).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed: HTTP ${response.code}")
            val body = response.body ?: throw IOException("Download returned no body")
            val total = release.assetSize.takeIf { it > 0 } ?: body.contentLength()
            part.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var written = 0L
                    var lastProgress = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = ((written * PERCENT) / total).toInt().coerceIn(0, PERCENT)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                _download.value = AppUpdateDownload.Downloading(release.tag, progress)
                            }
                        }
                    }
                }
            }
        }
        return files.commit(release).also { Logger.info(TAG, "Stored update ${it.name}") }
    }

    private suspend fun awaitNetwork() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val active = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        if (active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) return
        callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(Unit)
                }
            }
            connectivity.registerDefaultNetworkCallback(callback)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }.first()
    }

    private fun UpdateState.UpdateAvailable.toRelease(): AppUpdateRelease = AppUpdateRelease(
        tag = release.tagName,
        name = release.name,
        notes = release.body,
        assetName = apkAsset.name,
        assetUrl = apkAsset.downloadUrl,
        assetSize = apkAsset.size,
        assetDigest = apkAsset.digest,
        abi = UpdateRepository.abiLabelOf(apkAsset.name)
    )

    private companion object {
        const val TAG = "AppUpdateManager"
        const val STATE_FILE_NAME = "update_state.json"
        const val UPDATES_DIR_NAME = "updates"
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 60L
        const val DOWNLOAD_BUFFER_SIZE = 8192
        const val PERCENT = 100
    }
}
