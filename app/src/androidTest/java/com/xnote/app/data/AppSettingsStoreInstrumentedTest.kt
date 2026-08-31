package com.xnote.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xnote.app.data.settings.AppSettingsStore
import com.xnote.app.domain.model.BackgroundKey
import com.xnote.app.domain.model.GridBuiltinBackgroundId
import com.xnote.app.domain.model.defaultBackgroundKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// -- Tests

@RunWith(AndroidJUnit4::class)
class AppSettingsStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultBackgroundPersistsAcrossStoreInstances() = runTest {
        val store = AppSettingsStore(context)
        val selected = BackgroundKey(GridBuiltinBackgroundId)
        try {
            store.setDefaultBackground(selected)

            val reopened = AppSettingsStore(context)
            assertEquals(selected, reopened.settings.first().defaultBackground)
        } finally {
            store.setDefaultBackground(defaultBackgroundKey())
        }
    }
}
