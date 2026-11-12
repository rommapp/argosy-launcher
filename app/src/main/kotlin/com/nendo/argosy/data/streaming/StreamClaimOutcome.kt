package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMStreamBusyDetail
import com.nendo.argosy.data.remote.romm.RomMStreamCardImportDetail
import com.nendo.argosy.data.remote.romm.RomMStreamJoinableSession
import com.nendo.argosy.data.remote.romm.RomMStreamLaunching

sealed interface StreamClaimOutcome {
    data class Launching(val launch: RomMStreamLaunching) : StreamClaimOutcome

    data class Busy(
        val romName: String?,
        val claimedAt: String?,
        val draining: Boolean
    ) : StreamClaimOutcome

    data class CardDecision(
        val unreadable: Boolean,
        val fileCount: Int,
        val gameCodes: List<String>,
        val reason: String?
    ) : StreamClaimOutcome

    data object StartRefused : StreamClaimOutcome

    data class Refused(val detail: String?) : StreamClaimOutcome

    data object NotFound : StreamClaimOutcome

    data object Unreachable : StreamClaimOutcome

    data class Failed(val status: Int) : StreamClaimOutcome
}

/**
 * Maps a claim's answer to what the launch shows (backend/endpoints/streaming.py `claim_session`):
 * 409 a held container, 428 a memory card waiting on a decision, 403 a group without Streaming
 * Write, 400 a refused save or state pick.
 */
object StreamClaimErrors {
    fun classify(
        status: Int,
        busy: RomMStreamBusyDetail?,
        card: RomMStreamCardImportDetail?,
        detailText: String?
    ): StreamClaimOutcome = when (status) {
        HTTP_CONFLICT -> StreamClaimOutcome.Busy(
            romName = busy?.romName,
            claimedAt = busy?.claimedAt,
            draining = busy?.draining == true
        )
        HTTP_PRECONDITION_REQUIRED -> card
            ?.takeIf { it.code == RomMStreamCardImportDetail.CODE_IMPORT_REQUIRED }
            ?.let {
                StreamClaimOutcome.CardDecision(
                    unreadable = it.outcome == RomMStreamCardImportDetail.OUTCOME_UNREADABLE,
                    fileCount = it.summary?.fileCount ?: 0,
                    gameCodes = it.summary?.gameCodes.orEmpty(),
                    reason = it.reason
                )
            }
            ?: StreamClaimOutcome.Failed(status)
        HTTP_FORBIDDEN -> StreamClaimOutcome.StartRefused
        HTTP_BAD_REQUEST -> StreamClaimOutcome.Refused(detailText)
        HTTP_NOT_FOUND -> StreamClaimOutcome.NotFound
        else -> StreamClaimOutcome.Failed(status)
    }

    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_CONFLICT = 409
    private const val HTTP_PRECONDITION_REQUIRED = 428
}

/**
 * Join is offered on a held container only when RomM lists a joinable session for the same rom
 * (`GET /api/streaming/sessions/joinable?rom_id=`).
 */
object StreamBusyChoice {
    fun joinTarget(sessions: List<RomMStreamJoinableSession>, romId: Long): RomMStreamJoinableSession? =
        sessions.firstOrNull { it.romId == romId && !it.platform.isNullOrBlank() }
}
