package com.nendo.argosy.data.netplay

import android.content.Intent
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.NetplaySession

sealed class NetplayJoinState {
    data object Idle : NetplayJoinState()

    data class MatchingCore(
        val session: NetplaySession,
        val friend: Friend,
        val sub: CoreSubState
    ) : NetplayJoinState()

    data class VerifyingGame(
        val session: NetplaySession,
        val friend: Friend,
        val corePath: String?,
        val sub: VerifySubState
    ) : NetplayJoinState()

    data class JoiningSession(
        val session: NetplaySession,
        val friend: Friend
    ) : NetplayJoinState()

    data class LaunchReady(
        val intent: Intent,
        val gameId: Long
    ) : NetplayJoinState()

    data class Failed(val message: String) : NetplayJoinState()
    data object Cancelled : NetplayJoinState()
}

sealed class CoreSubState {
    data object Resolving : CoreSubState()
    data class Downloading(val pct: Float) : CoreSubState()
    data class Ready(val corePath: String) : CoreSubState()
}

sealed class VerifySubState {
    data object Probing : VerifySubState()

    data class AmbiguousCandidates(
        val candidates: List<JoinCandidate>,
        val selectedGameId: Long? = null,
        val downloadProgress: Float? = null,
        val focusIndex: Int = 0
    ) : VerifySubState()

    data class HashMismatchVariants(
        val gameId: Long,
        val gameTitle: String,
        val variants: List<JoinVariant>,
        val tryingFileId: Long? = null,
        val focusIndex: Int = 0
    ) : VerifySubState()

    data class Confirmed(
        val gameId: Long,
        val localPath: String
    ) : VerifySubState()
}

data class JoinCandidate(
    val gameId: Long,
    val title: String,
    val platformSlug: String,
    val platformName: String,
    val coverPath: String?,
    val isInstalled: Boolean,
    val rommId: Long?
)

data class JoinVariant(
    val fileId: Long,
    val fileName: String,
    val category: String?,
    val isInstalled: Boolean
)
