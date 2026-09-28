package com.xnote.app.debug

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.AtomicFile
import com.xnote.app.data.agent.*
import com.xnote.app.data.db.XNoteDatabase
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// -- Type Definitions

/** Shell-only Debug fixture. Run outside instrumentation so its foreground exemption cannot mask rejection. */
class AgentBackgroundRestrictionService : Service() {
    // -- Constants

    companion object {
        private const val DatabaseName = "agent-background-restriction-test.db"
        private const val ReportName = "agent-background-restriction-result.json"
        private const val TestInput = "后台受限后应保留的测试输入"
        private const val ContinueAction = "com.xnote.app.debug.CONTINUE_BACKGROUND_TEST"
    }

    // -- State and Variables

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: XNoteDatabase? = null
    private var timeline: AgentTimeline? = null
    private var phase = "idle"
    private var runId: String? = null
    private var platformException: String? = null
    private var importance = 0
    private var requests = 0

    // -- Functions

    private suspend fun arm() {
        check(phase == "idle")
        deleteDatabase(DatabaseName)
        val db = XNoteDatabase.create(this, DatabaseName).also { database = it }
        val secrets = ConcurrentHashMap<String, String>()
        val credentials = object : ModelCredentialStore {
            override fun read(reference: String) = requireNotNull(secrets[reference])
            override fun write(reference: String, secret: String) { secrets[reference] = secret }
            override fun delete(reference: String) { secrets.remove(reference) }
        }
        val profiles = ModelProfileStore(db, credentials)
        profiles.save(ModelProfile("background-restriction-test", name = "后台限制测试", protocol = ModelProtocol.OpenAI,
            modelId = "test", isDefault = true), "local-test-only")
        val model = object : ModelClient {
            override fun stream(profile: ModelProfile, apiKey: String, request: ModelRequest) = flow {
                requests++
                check(request.messages.any { it.text == TestInput })
                emit(ModelEvent.Text("前台继续完成"))
                emit(ModelEvent.Finished(ModelFinish.Complete))
            }
        }
        val execution = AgentTimeline(db, profiles, model, runScope, startBackground = {
            try { AgentRunService.start(this) } catch (error: Exception) {
                platformException = error.javaClass.name
                throw error
            }
        }).also { timeline = it }
        execution.awaitReady()
        report("armed")
        // Allow the host to press Home, then outlive the user-visible transition exemption.
        delay(35_000)
        importance = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance
        check(importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) { "应用仍可见：$importance" }
        execution.send(TestInput)
        withTimeout(10_000) { execution.state.first { !it.running } }
        val run = db.agent().unfinishedRuns().single()
        runId = run.id
        check(platformException == ForegroundServiceStartNotAllowedException::class.java.name) { "未收到系统后台启动拒绝：$platformException" }
        check(run.status == AgentRunStatus.Interrupted && run.errorCode == "background_unavailable")
        check(requests == 0)
        check(db.agent().messages().any { it.text == TestInput })
        check(execution.state.value.notice?.contains("回到前台后可继续") == true)
        report("restricted", run.status.name, run.errorCode)
    }

    private suspend fun continueFromForeground() {
        check(phase == "restricted")
        val currentImportance = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance
        check(currentImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) { "请先打开应用：$currentImportance" }
        val execution = requireNotNull(timeline)
        val db = requireNotNull(database)
        platformException = null
        execution.continueRun(requireNotNull(runId))
        withTimeout(10_000) { execution.state.first { !it.running } }
        val run = requireNotNull(db.agent().run(requireNotNull(runId)))
        check(run.status == AgentRunStatus.Complete)
        check(platformException == null && requests == 1)
        check(db.agent().messages().count { it.role == AgentMessageRole.User && it.text == TestInput } == 1)
        check(db.agent().messages().any { it.text == "前台继续完成" && it.status == AgentMessageStatus.Complete })
        report("passed", run.status.name, run.errorCode)
        cleanup()
        stopSelf()
    }

    private fun report(value: String, status: String? = null, error: String? = null) {
        phase = value
        val content = buildJsonObject {
            put("phase", value); put("pid", Process.myPid()); put("importanceAtBackgroundAttempt", importance)
            put("platformException", platformException); put("modelRequests", requests)
            put("runId", runId); put("runStatus", status); put("error", error)
        }.toString().toByteArray()
        val file = AtomicFile(File(filesDir, ReportName))
        val output = file.startWrite()
        try { output.write(content); file.finishWrite(output) } catch (failure: Exception) { file.failWrite(output); throw failure }
    }

    private suspend fun cleanup() {
        timeline?.clearChat()
        runScope.coroutineContext[Job]?.cancelAndJoin()
        database?.close()
        database = null
        deleteDatabase(DatabaseName)
    }

    // -- Lifecycle Hooks

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                if (intent?.action == ContinueAction) continueFromForeground() else arm()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                report("failed", error = failure.message ?: failure.javaClass.name)
                cleanup()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        runScope.cancel()
        database?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
