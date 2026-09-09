package com.nendo.argosy.domain.model

data class DeepLinkRequest(
    val gameId: Long? = null,
    val rommId: Long? = null,
    val romPath: String? = null,
    val channelName: String? = null
) {
    val hasTarget: Boolean
        get() = gameId != null || rommId != null || !romPath.isNullOrBlank()
}
