package com.nendo.argosy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.nendo.argosy.data.sync.SaveSyncDownloadObserver
import com.nendo.argosy.data.sync.SaveSyncWorker
import com.nendo.argosy.data.update.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ArgosyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var saveSyncDownloadObserver: SaveSyncDownloadObserver

    override fun onCreate() {
        super.onCreate()
        UpdateCheckWorker.schedule(this)
        SaveSyncWorker.schedule(this)
        saveSyncDownloadObserver.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
