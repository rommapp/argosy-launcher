package com.nendo.argosy.data.sync

import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformSyncQueue internal constructor(
    private val syncPlatformUseCase: dagger.Lazy<SyncPlatformUseCase>,
    private val syncLibraryUseCase: dagger.Lazy<SyncLibraryUseCase>,
    private val scope: CoroutineScope
) {
    @Inject
    constructor(
        syncPlatformUseCase: dagger.Lazy<SyncPlatformUseCase>,
        syncLibraryUseCase: dagger.Lazy<SyncLibraryUseCase>
    ) : this(syncPlatformUseCase, syncLibraryUseCase, CoroutineScope(Dispatchers.IO + SupervisorJob()))

    sealed interface Job {
        data class Library(val initializeFirst: Boolean, val changesSince: java.time.Instant? = null) : Job
        data class Platform(val id: Long, val name: String) : Job
    }

    private data class Pending(val job: Job, val onComplete: (() -> Unit)?)

    private val pending = ArrayDeque<Pending>()
    private val drainMutex = Mutex()
    private val lock = Any()

    private val _activeJob = MutableStateFlow<Job?>(null)
    val activeJob: StateFlow<Job?> = _activeJob.asStateFlow()

    private val _queuedPlatformIds = MutableStateFlow<Set<Long>>(emptySet())
    val queuedPlatformIds: StateFlow<Set<Long>> = _queuedPlatformIds.asStateFlow()

    private val _libraryQueued = MutableStateFlow(false)
    val libraryQueued: StateFlow<Boolean> = _libraryQueued.asStateFlow()

    val busyPlatformIds: StateFlow<Set<Long>> =
        combine(activeJob, queuedPlatformIds) { active, queued ->
            queued + ((active as? Job.Platform)?.id?.let { setOf(it) } ?: emptySet())
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val isLibraryBusy: StateFlow<Boolean> =
        combine(activeJob, libraryQueued) { active, queued ->
            queued || active is Job.Library
        }.stateIn(scope, SharingStarted.Eagerly, false)

    fun isPlatformBusy(platformId: Long): Boolean = synchronized(lock) {
        if (_libraryQueued.value) return true
        if (_activeJob.value is Job.Library) return true
        if (platformId in _queuedPlatformIds.value) return true
        val active = _activeJob.value
        active is Job.Platform && active.id == platformId
    }

    fun isLibraryBusyNow(): Boolean = synchronized(lock) {
        _libraryQueued.value || _activeJob.value is Job.Library
    }

    fun enqueuePlatform(
        platformId: Long,
        platformName: String,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        synchronized(lock) {
            if (isPlatformBusy(platformId)) return false
            pending.addLast(Pending(Job.Platform(platformId, platformName), onComplete))
            _queuedPlatformIds.update { it + platformId }
        }
        scope.launch { drain() }
        return true
    }

    /**
     * Queues a platform sync behind existing jobs, including any library sync.
     */
    fun enqueuePlatformAfterLibrary(platformId: Long, platformName: String) {
        synchronized(lock) {
            if (platformId in _queuedPlatformIds.value) return
            pending.addLast(Pending(Job.Platform(platformId, platformName), null))
            _queuedPlatformIds.update { it + platformId }
        }
        scope.launch { drain() }
    }

    fun enqueueLibrary(
        initializeFirst: Boolean = false,
        changesSince: java.time.Instant? = null,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        synchronized(lock) {
            if (isLibraryBusyNow()) return false
            pending.addLast(Pending(Job.Library(initializeFirst, changesSince), onComplete))
            _libraryQueued.value = true
        }
        scope.launch { drain() }
        return true
    }

    private suspend fun drain() {
        drainMutex.lock()
        try {
            while (true) {
                val item = synchronized(lock) { pending.removeFirstOrNull() } ?: break
                val job = item.job
                _activeJob.value = job
                try {
                    when (job) {
                        is Job.Platform -> syncPlatformUseCase.get().invoke(job.id, job.name)
                        is Job.Library -> syncLibraryUseCase.get()
                            .invoke(initializeFirst = job.initializeFirst, changesSince = job.changesSince)
                    }
                } finally {
                    synchronized(lock) {
                        when (job) {
                            is Job.Platform -> _queuedPlatformIds.update { it - job.id }
                            is Job.Library -> _libraryQueued.value = false
                        }
                        _activeJob.value = null
                    }
                    item.onComplete?.invoke()
                }
            }
        } finally {
            drainMutex.unlock()
        }
    }
}
