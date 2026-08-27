package com.xnote.app.data.recycle

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xnote.app.XNoteApplication
import java.util.concurrent.TimeUnit

// -- Type Definitions

class RecycleBinCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? XNoteApplication)?.container
            ?: return Result.success()
        val extra = container.settings.current().referencedAttachmentIds()
        container.noteLibrary.purgeExpiredTrash(extraReferencedAttachmentIds = extra)
        return Result.success()
    }

    companion object {
        const val UniqueName = "xnote-recycle-bin-cleanup"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UniqueName,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
