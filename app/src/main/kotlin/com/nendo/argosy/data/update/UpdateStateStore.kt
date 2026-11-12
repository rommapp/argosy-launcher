package com.nendo.argosy.data.update

import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.util.Logger
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The last update check's result and the release tag the user chose to skip. [skippedTag] is the
 * literal release tag, compared against later results and never shown.
 */
@JsonClass(generateAdapter = true)
data class StoredUpdateState(
    val latest: AppUpdateRelease? = null,
    val skippedTag: String? = null
) {
    fun withoutSkippedTag(): StoredUpdateState = copy(skippedTag = null)
}

/**
 * [StoredUpdateState] kept in one small json file. Every call does blocking file I/O, so callers
 * run it off the main thread. The first read or write loads the file.
 */
class UpdateStateStore(private val file: File) {

    private val adapter = Moshi.Builder().build().adapter(StoredUpdateState::class.java)
    private val lock = Any()
    private var loaded = false

    private val _state = MutableStateFlow(StoredUpdateState())
    val state: StateFlow<StoredUpdateState> = _state.asStateFlow()

    fun load(): StoredUpdateState = synchronized(lock) {
        ensureLoaded()
        _state.value
    }

    fun update(transform: (StoredUpdateState) -> StoredUpdateState): StoredUpdateState = synchronized(lock) {
        ensureLoaded()
        val next = transform(_state.value)
        if (next != _state.value) {
            write(next)
            _state.value = next
        }
        next
    }

    private fun ensureLoaded() {
        if (loaded) return
        _state.value = read()
        loaded = true
    }

    private fun read(): StoredUpdateState {
        if (!file.isFile) return StoredUpdateState()
        return try {
            adapter.fromJson(file.readText()) ?: StoredUpdateState()
        } catch (e: Exception) {
            Logger.warn(TAG, "Stored update state unreadable, starting empty: ${e.message}")
            StoredUpdateState()
        }
    }

    private fun write(state: StoredUpdateState) {
        file.parentFile?.mkdirs()
        val staging = File(file.parentFile, file.name + STAGING_SUFFIX)
        staging.writeText(adapter.toJson(state))
        if (!staging.renameTo(file)) {
            file.delete()
            if (!staging.renameTo(file)) {
                staging.delete()
                Logger.warn(TAG, "Could not write stored update state")
            }
        }
    }

    private companion object {
        const val TAG = "UpdateStateStore"
        const val STAGING_SUFFIX = ".staging"
    }
}
