package com.nendo.argosy.data.remote.romm

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays media stored in the connected RomM server's resources, such as a game's ScreenScraper
 * gameplay video, carrying the session token the way the image loader does.
 */
@Singleton
class RomMVideoSource @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val apiClient: RomMApiClient
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RomMImageAuthInterceptor(connectionManager))
            .build()
    }

    fun urlFor(resourcePath: String?): String? = apiClient.buildResourceUrl(resourcePath)

    @OptIn(UnstableApi::class)
    fun createPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(client)))
            .build()
}
