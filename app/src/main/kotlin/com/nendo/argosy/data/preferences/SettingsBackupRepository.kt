package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.storage.StoragePathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SettingsExportResult {
    data class Success(val path: String, val count: Int) : SettingsExportResult
    data class Error(val message: String) : SettingsExportResult
}

sealed interface SettingsImportResult {
    data class Success(val applied: Int, val skipped: Int) : SettingsImportResult
    data class Error(val message: String) : SettingsImportResult
}

/**
 * Reads and writes the plain-text settings backup described by [SettingsBackupKeys].
 *
 * Import is a merge rather than a replace. Only the keys the file names are written, so a value
 * the file omits keeps whatever the device already had, and a value the file carries under a name
 * or a type this build does not recognise is counted and dropped. That leaves a file hand-edited
 * by a user, or written by a different version, safe to apply without a schema negotiation.
 */
@Singleton
class SettingsBackupRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    suspend fun exportToFile(): SettingsExportResult = withContext(Dispatchers.IO) {
        try {
            val stored = dataStore.data.first().asMap()
            val values = JSONObject()
            SettingsBackupKeys.EXPORTED.forEach { entry ->
                val value = stored[keyFor(entry)] ?: return@forEach
                if (matches(entry.type, value)) values.put(entry.name, value)
            }
            val document = JSONObject().apply {
                put(FIELD_VERSION, FORMAT_VERSION)
                put(FIELD_APP_VERSION, BuildConfig.VERSION_NAME)
                put(FIELD_SETTINGS, values)
            }
            val target = File(targetPath())
            target.writeText(document.toString(2))
            SettingsExportResult.Success(target.absolutePath, values.length())
        } catch (e: Exception) {
            SettingsExportResult.Error(e.message ?: "Could not write the backup")
        }
    }

    suspend fun importFromFile(path: String): SettingsImportResult = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.isFile) return@withContext SettingsImportResult.Error("File not found")
            val settings = JSONObject(file.readText()).optJSONObject(FIELD_SETTINGS)
                ?: return@withContext SettingsImportResult.Error("Not an Argosy settings backup")
            var applied = 0
            var skipped = 0
            dataStore.edit { prefs ->
                settings.keys().forEach { name ->
                    val entry = SettingsBackupKeys.BY_NAME[name]
                    val wrote = entry != null && write(prefs, entry, settings.opt(name))
                    if (wrote) applied++ else skipped++
                }
            }
            SettingsImportResult.Success(applied, skipped)
        } catch (e: Exception) {
            SettingsImportResult.Error(e.message ?: "Could not read the backup")
        }
    }

    private fun write(prefs: MutablePreferences, entry: SettingsBackupKey, raw: Any?): Boolean =
        when (entry.type) {
            SettingsBackupType.BOOLEAN -> (raw as? Boolean)?.let {
                prefs[booleanPreferencesKey(entry.name)] = it
                true
            }
            SettingsBackupType.INT -> (raw as? Number)?.let {
                prefs[intPreferencesKey(entry.name)] = it.toInt()
                true
            }
            SettingsBackupType.LONG -> (raw as? Number)?.let {
                prefs[longPreferencesKey(entry.name)] = it.toLong()
                true
            }
            SettingsBackupType.FLOAT -> (raw as? Number)?.let {
                prefs[floatPreferencesKey(entry.name)] = it.toFloat()
                true
            }
            SettingsBackupType.STRING -> (raw as? String)?.let {
                prefs[stringPreferencesKey(entry.name)] = it
                true
            }
        } ?: false

    private fun matches(type: SettingsBackupType, value: Any): Boolean = when (type) {
        SettingsBackupType.BOOLEAN -> value is Boolean
        SettingsBackupType.INT -> value is Int
        SettingsBackupType.LONG -> value is Long
        SettingsBackupType.FLOAT -> value is Float
        SettingsBackupType.STRING -> value is String
    }

    private fun keyFor(entry: SettingsBackupKey): Preferences.Key<*> = when (entry.type) {
        SettingsBackupType.BOOLEAN -> booleanPreferencesKey(entry.name)
        SettingsBackupType.INT -> intPreferencesKey(entry.name)
        SettingsBackupType.LONG -> longPreferencesKey(entry.name)
        SettingsBackupType.FLOAT -> floatPreferencesKey(entry.name)
        SettingsBackupType.STRING -> stringPreferencesKey(entry.name)
    }

    companion object {
        const val FILE_NAME = "argosy-settings.json"
        private const val FORMAT_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_APP_VERSION = "app_version"
        private const val FIELD_SETTINGS = "settings"

        fun targetPath(): String = "${StoragePathUtils.primaryExternalRoot}/$FILE_NAME"
    }
}
