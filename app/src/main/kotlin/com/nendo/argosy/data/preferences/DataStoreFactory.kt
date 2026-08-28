package com.nendo.argosy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.nendo.argosy.util.Logger

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

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = recoverFromCorruption()
)

internal fun preferencesCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    recoverFromCorruption()
