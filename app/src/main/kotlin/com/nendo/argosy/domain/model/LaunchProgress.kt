package com.nendo.argosy.domain.model

data class LaunchProgress(
    val gameTitle: String?,
    val step: LaunchStep,
    val gameId: Long? = null,
    val stepStartedAtMs: Long = 0L,
    val failure: LaunchFailure? = null
)

sealed interface LaunchStep {
    data object FinishingPrevious : LaunchStep
    data object RecapLoading : LaunchStep
    data class Recap(val recap: LaunchRecap) : LaunchStep
    data class Save(val progress: SyncProgress) : LaunchStep
    sealed interface Question : LaunchStep {
        val options: List<LaunchPromptOption>
    }
    data class Prompt(val conflict: SyncProgress, override val options: List<LaunchPromptOption>) : Question
    data class ChooseLauncher(override val options: List<LaunchPromptOption>) : Question
    data class RunningGame(
        val kind: RunningGameQuestion,
        val emulatorName: String,
        val runningGameTitle: String?,
        override val options: List<LaunchPromptOption>
    ) : Question
    /**
     * The built-in games' slots are full: the player picks one of [candidates] to close so the
     * game being launched, [launchingTitle], has room. [stage] moves past CHOOSE on the same step
     * while [chosenTaskId] saves and closes.
     */
    data class MakeRoom(
        val launchingTitle: String?,
        val candidates: List<MakeRoomCandidate>,
        val initialFocus: Int,
        val stage: MakeRoomStage,
        val chosenTaskId: Int?
    ) : LaunchStep
    data class DownloadingCore(val fraction: Float?, val totalBytes: Long? = null) : LaunchStep
    data object Starting : LaunchStep
    data object PreparingSystemFiles : LaunchStep
    data object PreparingUi : LaunchStep
    data object Launching : LaunchStep
}

/**
 * The stretch of a launch a step belongs to. One clock runs for a phase across its steps;
 * QUESTION steps wait for an answer and LAUNCHING ends the launch.
 */
enum class LaunchPhase { PREVIOUS, RECAP_LOADING, SAVE, CORE, PREPARE, LAUNCHING, QUESTION }

/**
 * A fault the launch survives: the game still opens, with the save already on the device.
 */
enum class LaunchFailure { SERVER_UNREACHABLE, SERVER_TIMED_OUT, SAVE_DOWNLOAD_FAILED, SAVE_WRITE_FAILED }

val LaunchStep.phase: LaunchPhase
    get() = when (this) {
        LaunchStep.FinishingPrevious -> LaunchPhase.PREVIOUS
        LaunchStep.RecapLoading -> LaunchPhase.RECAP_LOADING
        is LaunchStep.Recap -> LaunchPhase.QUESTION
        is LaunchStep.Question -> LaunchPhase.QUESTION
        is LaunchStep.MakeRoom -> LaunchPhase.QUESTION
        is LaunchStep.Save -> LaunchPhase.SAVE
        is LaunchStep.DownloadingCore -> LaunchPhase.CORE
        LaunchStep.Starting -> LaunchPhase.PREPARE
        LaunchStep.PreparingSystemFiles -> LaunchPhase.PREPARE
        LaunchStep.PreparingUi -> LaunchPhase.PREPARE
        LaunchStep.Launching -> LaunchPhase.LAUNCHING
    }

val LaunchStep.failure: LaunchFailure?
    get() = (this as? LaunchStep.Save)?.progress?.launchFailure

val LaunchStep.fraction: Float?
    get() = (this as? LaunchStep.DownloadingCore)?.fraction

val LaunchStep.sizeBytes: Long?
    get() = (this as? LaunchStep.DownloadingCore)?.totalBytes?.takeIf { it > 0 }

private val SyncProgress.launchFailure: LaunchFailure?
    get() = when (this) {
        is SyncProgress.PreLaunch.Connecting -> LaunchFailure.SERVER_UNREACHABLE.takeIf { success == false }
        is SyncProgress.PreLaunch.Writing -> LaunchFailure.SAVE_WRITE_FAILED.takeIf { success == false }
        is SyncProgress.PreLaunch.TimedOut -> LaunchFailure.SERVER_TIMED_OUT
        is SyncProgress.Error -> LaunchFailure.SAVE_DOWNLOAD_FAILED
        else -> null
    }

/**
 * Why a launch stops on a game an emulator is keeping in the background. EMULATOR_BUSY: the
 * emulator the launch needs is running another game.
 */
enum class RunningGameQuestion { EMULATOR_BUSY }

/**
 * An open built-in game the player can close to make room. [snapshotPath] is the game's last
 * frame, null where none could be read; [snapshotTakenAtMillis] is 0 then.
 */
data class MakeRoomCandidate(
    val taskId: Int,
    val slotIndex: Int,
    val gameId: Long,
    val title: String?,
    val snapshotPath: String?,
    val snapshotTakenAtMillis: Long,
    val lastSavedAtMillis: Long?
)

enum class MakeRoomStage { CHOOSE, SAVING, CLOSED }

enum class LaunchPromptOption {
    KEEP_HARDCORE,
    DOWNGRADE_TO_CASUAL,
    SKIP_HARDCORE_SAVE,
    APPLY_LOCAL,
    RESTORE_SERVER,
    LAUNCH_WITHOUT_SYNC,
    KEEP_MINE,
    TAKE_THEIRS,
    BRANCH_LOCAL,
    REVERT_LOCAL,
    START_GAME,
    LAUNCH_WITH_GAMENATIVE,
    LAUNCH_WITH_DROIDDECK,
    CLOSE_AND_LAUNCH
}
