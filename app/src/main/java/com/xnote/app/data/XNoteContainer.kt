package com.xnote.app.data

import android.content.Context
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.data.files.AttachmentFileStore
import com.xnote.app.data.recycle.RecycleBinCleanupWorker
import com.xnote.app.data.repository.NoteLibrary
import com.xnote.app.data.settings.AppSettingsStore
import com.xnote.app.domain.model.EpochClock
import com.xnote.app.domain.model.SystemEpochClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// -- Type Definitions

class XNoteContainer(
    context: Context,
    clock: EpochClock = SystemEpochClock,
) {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: XNoteDatabase = XNoteDatabase.create(appContext)
    val settings = AppSettingsStore(appContext)
    val noteLibrary = NoteLibrary(
        database = database,
        files = AttachmentFileStore(appContext.filesDir),
        clock = clock,
    )

    fun start() {
        RecycleBinCleanupWorker.enqueue(appContext)
        applicationScope.launch {
            val extra = settings.current().referencedAttachmentIds()
            noteLibrary.purgeExpiredTrash(extraReferencedAttachmentIds = extra)
        }
    }
}
