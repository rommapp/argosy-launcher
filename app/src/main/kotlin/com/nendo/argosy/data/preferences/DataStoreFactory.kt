package com.nendo.argosy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.createMultiProcessCoordinator
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File

private const val TAG = "DataStore"

/**
 * Starts from empty when the preferences file cannot be parsed, rather than letting the read throw.
 *
 * DataStore renames a temp file into place, which survives the process dying, but not the kernel
 * losing unflushed pages when the power is cut: the file is left truncated and every later read
 * fails to parse it. Rethrowing is the default, and preferences are read during startup, so the
 * throw arrives before there is a UI to report it and does so on every launch afterwards. That is
 * a crash loop only a data wipe clears, which is what users hitting it have had to do.
 *
 * Settings are the cost of recovering, and a settings backup can restore them. An app that will
 * not open cannot.
 */
private fun recoverFromCorruption() = ReplaceFileCorruptionHandler<Preferences> { cause ->
    Logger.error(TAG, "preferences file unreadable, starting from empty", cause)
    emptyPreferences()
}

internal fun multiProcessPreferencesStore(
    file: File,
    scope: CoroutineScope,
    coordinator: (File) -> InterProcessCoordinator = { createMultiProcessCoordinator(Dispatchers.IO, it) }
): DataStore<Preferences> =
    MultiProcessDataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = PreferencesSerializer,
            coordinatorProducer = { path, _ -> coordinator(path.toFile()) },
            producePath = { file.absolutePath.toPath() }
        ),
        corruptionHandler = recoverFromCorruption(),
        scope = scope
    )

private object SettingsStore {
    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: multiProcessPreferencesStore(
                file = context.applicationContext.preferencesDataStoreFile(SETTINGS_FILE),
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            ).also { instance = it }
        }
}

private const val SETTINGS_FILE = "settings"

val Context.dataStore: DataStore<Preferences>
    get() = SettingsStore.get(this)
