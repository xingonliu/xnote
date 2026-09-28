package com.xnote.app.data.agent

import android.content.Context
import androidx.work.*
import com.xnote.app.XNoteApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

// -- Type Definitions

class AgentMemoryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    // -- Functions

    override suspend fun doWork(): Result {
        val container = (applicationContext as XNoteApplication).container
        container.agentTimeline.awaitReady()
        return try {
            val episodesPending = container.agentTimeline.episodeStore.process(container.modelProfiles, container.modelClient)
            val notesPending = container.agentTimeline.noteMemory.process(container.modelProfiles, container.modelClient)
            if (episodesPending || notesPending) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            if (currentCoroutineContext().isActive && cancelled.message == "foreground_priority") Result.retry() else throw cancelled
        }
    }

    companion object {
        // -- Functions

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AgentMemoryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("xnote-agent-memory", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
