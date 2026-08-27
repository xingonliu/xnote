package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xnote.app.data.search.SearchHistoryStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// -- Tests

@RunWith(AndroidJUnit4::class)
class SearchHistoryStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recentQueriesPersistDeduplicateAndStayBounded() = runTest {
        val store = SearchHistoryStore(context)
        store.clear()
        try {
            (1..11).forEach { store.record("查询 $it") }
            store.record("  查询   5  ")

            val reopened = SearchHistoryStore(context)
            val recent = reopened.recentQueries.first()

            assertEquals(10, recent.size)
            assertEquals("查询 5", recent.first())
            assertEquals(1, recent.count { it == "查询 5" })
            assertEquals("查询 11", recent[1])
        } finally {
            store.clear()
        }
    }
}
