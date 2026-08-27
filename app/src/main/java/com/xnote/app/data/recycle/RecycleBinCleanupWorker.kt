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
        container.noteLibrary.purgeExpiredTrash()
        return Result.success()
    }

    companion object {
        const val UniqueName = "xnote-recycle-bin-cleanup"

        fun enqueue(context: Context) {
            // XNoteContainer performs the immediate sweep; the periodic job starts later so it
            // cannot compete with the first rendered frame on a cold install.
            val request = PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UniqueName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
