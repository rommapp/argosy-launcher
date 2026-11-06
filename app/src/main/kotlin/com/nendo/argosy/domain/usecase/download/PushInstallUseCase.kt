package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.data.preferences.DownloadDefaults
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

private const val TAG = "PushInstallUseCase"

sealed interface PushInstallOutcome {
    data object Queued : PushInstallOutcome
    data object AlreadyInstalled : PushInstallOutcome
    data class Failed(val failure: PushInstallFailure, val detail: String? = null) : PushInstallOutcome
}

enum class PushInstallFailure {
    ROM_UNRESOLVED,
    NO_EMULATOR,
    NO_INSTALLABLE_FILES,
    DOWNLOAD_ERROR
}

/**
 * Lands one install request from RomM in the download queue after a single-rom fetch. A platform
 * the fetch created is shown; an existing platform keeps its visibility.
 */
class PushInstallUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val gameFileDao: GameFileDao,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val emulatorDetector: EmulatorDetector,
    private val builtinPreferences: BuiltinEmulatorPreferencesRepository,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val apkInstallManager: ApkInstallManager
) {
    suspend operator fun invoke(romId: Long, requestedFileIds: List<Long>): PushInstallOutcome {
        val builtinEnabled = builtinPreferences.isBuiltinLibretroEnabled().first()
        val knownPlatformIds = platformRepository.getAllPlatformIds()
        val game = resolveGame(romId)
            ?: return PushInstallOutcome.Failed(PushInstallFailure.ROM_UNRESOLVED)
        if (game.platformId !in knownPlatformIds) platformRepository.updateVisibility(game.platformId, true)

        if (gameRepository.validateAndDiscoverGame(game.id)) return PushInstallOutcome.AlreadyInstalled
        if (!hasEmulatorFor(game, builtinEnabled)) return PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR)

        val selection = selectInstallFiles(gameFileDao.getFilesForGame(game.id), requestedFileIds)
        if (requestedFileIds.isNotEmpty() && selection == null) {
            return PushInstallOutcome.Failed(PushInstallFailure.NO_INSTALLABLE_FILES)
        }

        if (game.platformId == LocalPlatformIds.ANDROID) apkInstallManager.markPushed(game.id)

        return when (val result = downloadGameUseCase(game.id, selection)) {
            DownloadResult.Queued, is DownloadResult.MultiDiscQueued -> PushInstallOutcome.Queued
            DownloadResult.AlreadyDownloaded -> PushInstallOutcome.AlreadyInstalled
            is DownloadResult.Error -> if (result.reason == DownloadGameFailureReason.AllDiscsAlreadyDownloaded) {
                PushInstallOutcome.AlreadyInstalled
            } else {
                PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, result.reason.serverDetail())
            }
            is DownloadResult.ExtractionFailed -> PushInstallOutcome.Failed(
                PushInstallFailure.DOWNLOAD_ERROR,
                "previous extraction failed"
            )
        }
    }

    private fun DownloadGameFailureReason.serverDetail(): String = when (this) {
        DownloadGameFailureReason.GameNotFound -> "game not found"
        DownloadGameFailureReason.GameNotSynced -> "game not synced"
        is DownloadGameFailureReason.InvalidFileType -> "invalid file type .$extension"
        is DownloadGameFailureReason.RomInfoFetchFailed -> "rom info fetch failed: $serverMessage"
        DownloadGameFailureReason.NoDiscsFound -> "no discs found"
        DownloadGameFailureReason.AllDiscsAlreadyDownloaded -> "all discs already downloaded"
        DownloadGameFailureReason.NoDiscsQueued -> "no discs queued"
        DownloadGameFailureReason.GameNotFoundForRepair -> "game not found"
        DownloadGameFailureReason.NotMultiDisc -> "not multi-disc"
        is DownloadGameFailureReason.Extraction -> "extraction failed"
    }

    private suspend fun resolveGame(romId: Long): GameEntity? {
        val synced = try {
            romMRepository.syncSingleRom(romId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RomMResult.Error(e.message ?: "single-rom sync failed")
        }
        return when (synced) {
            is RomMResult.Success -> synced.data
            is RomMResult.Error -> {
                Logger.warn(TAG, "resolveGame: rom $romId not refreshed: ${synced.message} (${synced.code})")
                null
            }
        }
    }

    private suspend fun hasEmulatorFor(game: GameEntity, builtinEnabled: Boolean): Boolean {
        if (game.platformId == LocalPlatformIds.ANDROID) return true
        if (emulatorDetector.getPreferredEmulator(game.platformSlug, builtinEnabled) != null) return true
        emulatorDetector.detectEmulators()
        return emulatorDetector.getPreferredEmulator(game.platformSlug, builtinEnabled) != null
    }

    private fun selectInstallFiles(files: List<GameFileEntity>, requested: List<Long>): List<Long>? {
        if (requested.isEmpty()) return null
        if (files.isEmpty()) return requested
        val byRommId = files.associateBy { it.rommFileId }
        val chosen = requested.filter { id ->
            byRommId[id]?.let { VariantCategory.fromKey(it.category).isInstallable } == true
        }
        if (chosen.isEmpty()) return null
        val documents = files
            .filter { it.category in DownloadDefaults.DOCUMENT_KEYS }
            .mapNotNull { it.rommFileId }
        return (chosen + documents).distinct()
    }
}
