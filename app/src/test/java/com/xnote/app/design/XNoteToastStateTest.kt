package com.xnote.app.design

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// -- Type Definitions

@OptIn(ExperimentalCoroutinesApi::class)
class XNoteToastStateTest {
    // -- Functions

    @Test
    fun pageCancellationKeepsCurrentAndQueuedNoticesAlive() = runTest {
        val toast = XNoteToastState(backgroundScope)
        val page = CoroutineScope(coroutineContext + Job())
        page.launch {
            toast.show("已保存")
            toast.show("已恢复")
        }
        runCurrent()
        page.cancel()
        runCurrent()
        assertEquals("已保存", toast.hostState.currentSnackbarData?.visuals?.message)
        toast.hostState.currentSnackbarData?.dismiss()
        runCurrent()
        assertEquals("已恢复", toast.hostState.currentSnackbarData?.visuals?.message)
        toast.hostState.currentSnackbarData?.dismiss()
        runCurrent()
        assertNull(toast.hostState.currentSnackbarData)
    }

    @Test
    fun appDisposalClearsCurrentAndQueuedNotices() = runTest {
        val appScope = CoroutineScope(coroutineContext + Job())
        val toast = XNoteToastState(appScope)
        toast.show("第一条")
        toast.show("第二条")
        runCurrent()
        appScope.cancel()
        runCurrent()
        assertNull(toast.hostState.currentSnackbarData)
    }
}
