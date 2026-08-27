package com.xnote.app.data.search

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// -- Type Definitions

interface SearchHistoryRepository {
    val recentQueries: Flow<List<String>>

    suspend fun record(query: String)

    suspend fun clear()
}

object EmptySearchHistoryRepository : SearchHistoryRepository {
    override val recentQueries: Flow<List<String>> = flowOf(emptyList())

    override suspend fun record(query: String) = Unit

    override suspend fun clear() = Unit
}
