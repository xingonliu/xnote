package com.xnote.app.data.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// -- Type Definitions

class SearchHistoryStore(
    context: Context,
) : SearchHistoryRepository {
    private val dataStore = context.applicationContext.searchHistoryDataStore

    override val recentQueries: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[RecentQueriesKey]
            ?.let { encoded ->
                runCatching {
                    Json.decodeFromString(SearchHistorySerializer, encoded)
                }.getOrNull()
            }
            .orEmpty()
    }

    override suspend fun record(query: String) {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[RecentQueriesKey]
                ?.let { encoded ->
                    runCatching {
                        Json.decodeFromString(SearchHistorySerializer, encoded)
                    }.getOrNull()
                }
                .orEmpty()
            val updated = listOf(normalized) + current.filterNot {
                it.equals(normalized, ignoreCase = true)
            }
            preferences[RecentQueriesKey] = Json.encodeToString(
                SearchHistorySerializer,
                updated.take(MaximumRecentSearches),
            )
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(RecentQueriesKey)
        }
    }
}

// -- Constants

private const val MaximumRecentSearches = 10
private val RecentQueriesKey = stringPreferencesKey("recent_queries")
private val SearchHistorySerializer = ListSerializer(String.serializer())

// -- State

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xnote_search_history",
)
