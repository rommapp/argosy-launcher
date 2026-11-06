package com.nendo.argosy.data.netplay

import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.resolvedFor
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NetplayJoin"

@Singleton
class NetplayJoinService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preflight: NetplayPreflightChecker,
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val coreManager: LibretroCoreManager,
    private val downloadManager: DownloadManager,
    private val launchGameUseCase: LaunchGameUseCase,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var flowJob: Job? = null

    private val _state = MutableStateFlow<NetplayJoinState>(NetplayJoinState.Idle)
    val state: StateFlow<NetplayJoinState> = _state.asStateFlow()

    fun start(session: NetplaySession, friend: Friend) {
        flowJob?.cancel()
        flowJob = scope.launch { runFlow(session, friend) }
    }

    fun selectCandidate(gameId: Long) {
        val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
        val sub = current.sub as? VerifySubState.AmbiguousCandidates ?: return
        val candidate = sub.candidates.firstOrNull { it.gameId == gameId } ?: return
        flowJob?.cancel()
        flowJob = scope.launch {
            _state.value = current.copy(sub = sub.copy(selectedGameId = gameId, downloadProgress = null))
            verifyOrDownload(current.session, current.friend, current.corePath, candidate)
        }
    }

    fun selectVariant(fileId: Long) {
        val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
        val sub = current.sub as? VerifySubState.HashMismatchVariants ?: return
        val variant = sub.variants.firstOrNull { it.fileId == fileId } ?: return
        flowJob?.cancel()
        flowJob = scope.launch {
            _state.value = current.copy(sub = sub.copy(tryingFileId = fileId))
            verifyVariantFile(current.session, current.friend, current.corePath, sub.gameId, variant, sub)
        }
    }

    fun cancel() {
        flowJob?.cancel()
        flowJob = null
        _state.value = NetplayJoinState.Cancelled
    }

    fun reset() {
        flowJob?.cancel()
        flowJob = null
        _state.value = NetplayJoinState.Idle
    }

    fun moveCandidateFocus(delta: Int) {
        val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
        val sub = current.sub as? VerifySubState.AmbiguousCandidates ?: return
        if (sub.candidates.isEmpty()) return
        val next = (sub.focusIndex + delta).coerceIn(0, sub.candidates.size - 1)
        if (next == sub.focusIndex) return
        _state.value = current.copy(sub = sub.copy(focusIndex = next))
    }

    fun moveVariantFocus(delta: Int) {
        val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
        val sub = current.sub as? VerifySubState.HashMismatchVariants ?: return
        if (sub.variants.isEmpty()) return
        val next = (sub.focusIndex + delta).coerceIn(0, sub.variants.size - 1)
        if (next == sub.focusIndex) return
        _state.value = current.copy(sub = sub.copy(focusIndex = next))
    }

    fun confirmFocused() {
        val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
        when (val sub = current.sub) {
            is VerifySubState.AmbiguousCandidates -> sub.candidates.getOrNull(sub.focusIndex)?.let { selectCandidate(it.gameId) }
            is VerifySubState.HashMismatchVariants -> sub.variants.getOrNull(sub.focusIndex)?.let { selectVariant(it.fileId) }
            else -> Unit
        }
    }

    private suspend fun runFlow(session: NetplaySession, friend: Friend) {
        _state.value = NetplayJoinState.MatchingCore(session, friend, CoreSubState.Resolving)
        val corePath = when (val result = preflight.resolveCoreStep(session)) {
            is CoreResolveResult.Ready -> result.corePath
            is CoreResolveResult.NeedsDownload -> {
                _state.value = NetplayJoinState.MatchingCore(session, friend, CoreSubState.Downloading(0f))
                val dl = coreManager.downloadCoreById(result.coreId)
                if (dl.isFailure) {
                    _state.value = NetplayJoinState.Failed("Failed to download core: ${dl.exceptionOrNull()?.message ?: "unknown"}")
                    return
                }
                dl.getOrNull()?.absolutePath ?: run {
                    _state.value = NetplayJoinState.Failed("Core download returned no path")
                    return
                }
            }
            CoreResolveResult.Unsupported -> {
                _state.value = NetplayJoinState.Failed("Core unsupported")
                return
            }
            CoreResolveResult.VersionMismatch -> {
                _state.value = NetplayJoinState.Failed("Different core version")
                return
            }
        }
        _state.value = NetplayJoinState.MatchingCore(session, friend, CoreSubState.Ready(corePath))

        _state.value = NetplayJoinState.VerifyingGame(session, friend, corePath, VerifySubState.Probing)
        val candidates = preflight.findCandidatesStep(session)
        if (candidates.isEmpty()) {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
            return
        }

        val autoMatched = tryAutoMatch(candidates, session.romHashPrefix)
        if (autoMatched != null) {
            completeVerify(session, friend, corePath, autoMatched.first, autoMatched.second)
            return
        }

        val joinCandidates = buildJoinCandidates(candidates)
        _state.value = NetplayJoinState.VerifyingGame(
            session = session,
            friend = friend,
            corePath = corePath,
            sub = VerifySubState.AmbiguousCandidates(joinCandidates)
        )
    }

    private suspend fun tryAutoMatch(
        candidates: List<GameEntity>,
        expectedHash: String?
    ): Pair<Long, String>? {
        if (expectedHash.isNullOrEmpty()) return null
        for (game in candidates) {
            val result = preflight.verifyGameStep(game, expectedHash)
            if (result is GameVerifyResult.Matched) return game.id to result.localPath
        }
        return null
    }

    private suspend fun buildJoinCandidates(candidates: List<GameEntity>): List<JoinCandidate> {
        val art = gameArtDao.resolvedFor(candidates.map { it.id })
        return candidates.map { game ->
            val platform = platformDao.getById(game.platformId)
            val files = gameFileDao.getFilesForGame(game.id)
            val hasAnyFile = files.any { !it.localPath.isNullOrEmpty() } || !game.localPath.isNullOrEmpty()
            JoinCandidate(
                gameId = game.id,
                title = game.title,
                platformSlug = game.platformSlug,
                platformName = platform?.name ?: game.platformSlug,
                coverPath = art[game.id]?.coverPath,
                isInstalled = hasAnyFile,
                rommId = game.rommId
            )
        }
    }

    private suspend fun verifyOrDownload(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        candidate: JoinCandidate
    ) {
        val game = gameDao.getById(candidate.gameId) ?: run {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
            return
        }

        if (candidate.isInstalled) {
            verifyAfterInstall(session, friend, corePath, game)
            return
        }

        if (candidate.rommId == null) {
            _state.value = NetplayJoinState.Failed("ROM is not downloadable")
            return
        }

        awaitDownload(session, friend, corePath, game, candidate)
    }

    private suspend fun awaitDownload(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        game: GameEntity,
        candidate: JoinCandidate
    ) {
        val rommId = candidate.rommId ?: return
        val fileName = game.rommFileName ?: "${game.title}.rom"
        downloadManager.enqueueDownload(
            gameId = game.id,
            rommId = rommId,
            fileName = fileName,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            coverPath = candidate.coverPath,
            expectedSizeBytes = game.fileSizeBytes ?: 0L,
            isMultiFileRom = game.isMultiDisc,
            startNow = true
        )
        while (true) {
            val current = _state.value as? NetplayJoinState.VerifyingGame ?: return
            val sub = current.sub as? VerifySubState.AmbiguousCandidates ?: return
            val progress = downloadManager.state.value.activeDownloads.firstOrNull { it.gameId == game.id }
            val queued = downloadManager.state.value.queue.firstOrNull { it.gameId == game.id }
            if (progress == null && queued == null) {
                val refreshed = gameDao.getById(game.id)
                val hasFile = !refreshed?.localPath.isNullOrEmpty() ||
                    gameFileDao.getFilesForGame(game.id).any { !it.localPath.isNullOrEmpty() }
                if (hasFile) {
                    _state.value = current.copy(sub = sub.copy(downloadProgress = 1f))
                    verifyAfterInstall(session, friend, corePath, refreshed ?: game)
                } else {
                    _state.value = NetplayJoinState.Failed("Failed to download ROM")
                }
                return
            }
            if (progress != null) {
                val state = progress.state
                if (state == DownloadState.FAILED) {
                    _state.value = NetplayJoinState.Failed("Failed to download ROM")
                    return
                }
                if (state == DownloadState.COMPLETED) {
                    _state.value = current.copy(sub = sub.copy(downloadProgress = 1f))
                    val refreshed = gameDao.getById(game.id) ?: game
                    verifyAfterInstall(session, friend, corePath, refreshed)
                    return
                }
                val pct = if (progress.totalBytes > 0) progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat() else 0f
                _state.value = current.copy(sub = sub.copy(downloadProgress = pct.coerceIn(0f, 1f)))
            }
            kotlinx.coroutines.delay(250)
        }
    }

    private suspend fun verifyAfterInstall(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        game: GameEntity
    ) {
        val result = preflight.verifyGameStep(game, session.romHashPrefix)
        when (result) {
            is GameVerifyResult.Matched -> completeVerify(session, friend, corePath, game.id, result.localPath)
            is GameVerifyResult.NoMatch -> offerVariants(session, friend, corePath, game)
            is GameVerifyResult.NeedsDownload -> {
                _state.value = NetplayJoinState.Failed("Failed to download ROM")
            }
        }
    }

    private suspend fun offerVariants(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        game: GameEntity
    ) {
        val allFiles = gameFileDao.getFilesForGame(game.id)
        val variants = allFiles
            .filter { !it.localPath.isNullOrEmpty() }
            .filter { it.id != game.activeVariantFileId }
            .map { file ->
                JoinVariant(
                    fileId = file.id,
                    fileName = file.fileName,
                    category = file.category,
                    isInstalled = !file.localPath.isNullOrEmpty()
                )
            }
        if (variants.isEmpty()) {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
            return
        }
        _state.value = NetplayJoinState.VerifyingGame(
            session = session,
            friend = friend,
            corePath = corePath,
            sub = VerifySubState.HashMismatchVariants(
                gameId = game.id,
                gameTitle = game.title,
                variants = variants
            )
        )
    }

    private suspend fun verifyVariantFile(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        gameId: Long,
        variant: JoinVariant,
        sub: VerifySubState.HashMismatchVariants
    ) {
        val file = gameFileDao.getFilesForGame(gameId).firstOrNull { it.id == variant.fileId } ?: run {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
            return
        }
        val localPath = file.localPath ?: run {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
            return
        }
        val existing = file.romHashPrefix
        val hash = existing ?: run {
            val computed = RomHashComputer.computeRomHashPrefix(java.io.File(localPath))
            if (computed != null) gameFileDao.updateRomHashPrefix(file.id, computed)
            computed
        }
        if (hash != null && hash.equals(session.romHashPrefix, ignoreCase = true)) {
            completeVerify(session, friend, corePath, gameId, localPath)
            return
        }
        val remaining = sub.variants.filter { it.fileId != variant.fileId }
        if (remaining.isEmpty()) {
            _state.value = NetplayJoinState.Failed("No matching ROM could be found")
        } else {
            _state.value = NetplayJoinState.VerifyingGame(
                session = session,
                friend = friend,
                corePath = corePath,
                sub = sub.copy(variants = remaining, tryingFileId = null, focusIndex = 0)
            )
        }
    }

    private suspend fun completeVerify(
        session: NetplaySession,
        friend: Friend,
        corePath: String?,
        gameId: Long,
        localPath: String
    ) {
        _state.value = NetplayJoinState.VerifyingGame(
            session = session,
            friend = friend,
            corePath = corePath,
            sub = VerifySubState.Confirmed(gameId, localPath)
        )
        _state.value = NetplayJoinState.JoiningSession(session, friend)
        val launch = launchGameUseCase(gameId = gameId, allowVariantPrompt = false)
        if (launch is LaunchResult.Cancelled) {
            _state.value = NetplayJoinState.Idle
            return
        }
        if (launch !is LaunchResult.Success) {
            _state.value = NetplayJoinState.Failed("Couldn't launch game")
            return
        }
        val intent = Intent(launch.intent).apply {
            putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_SESSION_ID, session.sessionId)
            putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_HOST_USER_ID, friend.id)
            if (!corePath.isNullOrEmpty()) {
                putExtra(LibretroActivity.EXTRA_CORE_PATH, corePath)
            }
        }
        Logger.info(TAG, "Join flow ready: game=$gameId core=$corePath")
        _state.value = NetplayJoinState.LaunchReady(intent, gameId)
    }
}
