package com.nendo.argosy

import android.content.Context
import android.content.Intent
import android.net.Uri

object ArgosyHomeIntent {
    const val EXTRA_OPEN_RUNNING = "com.nendo.argosy.extra.OPEN_RUNNING"
    const val RUNNING_DEEP_LINK_HOST = "running"
    const val GAME_DEEP_LINK_HOST = "game"

    fun create(context: Context, openRunning: Boolean = false): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .apply { if (openRunning) putExtra(EXTRA_OPEN_RUNNING, true) }

    fun gameDetail(context: Context, gameId: Long): Intent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.Builder().scheme("argosy").authority(GAME_DEEP_LINK_HOST).appendPath(gameId.toString()).build()
        )
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    fun runningDeepLinkOf(intent: Intent?): Uri? =
        if (intent?.getBooleanExtra(EXTRA_OPEN_RUNNING, false) == true) {
            Uri.Builder().scheme("argosy").authority(RUNNING_DEEP_LINK_HOST).build()
        } else {
            null
        }
}
