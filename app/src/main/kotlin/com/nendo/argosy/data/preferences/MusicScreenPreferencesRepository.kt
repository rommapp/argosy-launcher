package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicSourceFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class MusicScreenPreferences(
    val grouping: MusicGrouping = MusicGrouping.ARTISTS,
    val sourceFilter: MusicSourceFilter = MusicSourceFilter.ALL
)

@Singleton
class MusicScreenPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val GROUP_BY = stringPreferencesKey("music_group_by")
        val SOURCE_FILTER = stringPreferencesKey("music_source_filter")
    }

    val preferences: Flow<MusicScreenPreferences> = dataStore.data
        .map { prefs ->
            MusicScreenPreferences(
                grouping = MusicGrouping.fromToken(prefs[Keys.GROUP_BY]),
                sourceFilter = MusicSourceFilter.fromToken(prefs[Keys.SOURCE_FILTER])
            )
        }
        .distinctUntilChanged()

    suspend fun setGrouping(grouping: MusicGrouping) {
        dataStore.edit { it[Keys.GROUP_BY] = grouping.token }
    }

    suspend fun setSourceFilter(filter: MusicSourceFilter) {
        dataStore.edit { it[Keys.SOURCE_FILTER] = filter.token }
    }
}
