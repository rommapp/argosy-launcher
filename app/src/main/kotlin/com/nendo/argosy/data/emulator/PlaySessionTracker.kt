package com.nendo.argosy.data.emulator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.nendo.argosy.data.local.dao.GameDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveSession(
    val gameId: Long,
    val startTime: Instant,
    val emulatorPackage: String
)

@Singleton
class PlaySessionTracker @Inject constructor(
    private val application: Application,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private var wasInBackground = false
    private var lastPauseTime: Instant? = null

    init {
        registerLifecycleCallbacks()
    }

    fun startSession(gameId: Long, emulatorPackage: String) {
        _activeSession.value = ActiveSession(
            gameId = gameId,
            startTime = Instant.now(),
            emulatorPackage = emulatorPackage
        )
    }

    fun endSession() {
        val session = _activeSession.value ?: return
        _activeSession.value = null

        val duration = Duration.between(session.startTime, Instant.now())
        val minutes = duration.toMinutes().toInt()

        if (minutes > 0) {
            scope.launch {
                gameDao.addPlayTime(session.gameId, minutes)
            }
        }
    }

    private fun registerLifecycleCallbacks() {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                if (wasInBackground && _activeSession.value != null) {
                    wasInBackground = false
                }
            }

            override fun onActivityPaused(activity: Activity) {
                lastPauseTime = Instant.now()
            }

            override fun onActivityStopped(activity: Activity) {
                wasInBackground = true

                val pauseTime = lastPauseTime ?: return
                val elapsed = Duration.between(pauseTime, Instant.now())

                if (elapsed.toMinutes() > 30 && _activeSession.value != null) {
                    endSession()
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    fun getSessionDuration(): Duration? {
        val session = _activeSession.value ?: return null
        return Duration.between(session.startTime, Instant.now())
    }
}
