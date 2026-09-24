package com.xnote.app.data.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.xnote.app.MainActivity
import com.xnote.app.R
import com.xnote.app.XNoteApplication
import com.xnote.app.domain.agent.AgentRunLimits
import kotlinx.coroutines.*

// -- Type Definitions

class AgentRunService : Service() {
    // -- Constants

    companion object {
        const val StopAction = "com.xnote.app.agent.STOP"
        private const val ChannelId = "agent_execution"
        private const val NotificationId = 114

        // -- State and Variables

        @Volatile private var active = false

        // -- Functions

        fun start(context: Context) {
            val intent = Intent(context, AgentRunService::class.java)
            if (active) context.startService(intent) else context.startForegroundService(intent)
        }
    }

    // -- State and Variables

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false

    // -- Derived Values

    private val timeline get() = (application as XNoteApplication).container.agentTimeline

    // -- Functions

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 0, Intent(this, AgentRunService::class.java).setAction(StopAction), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Agent 正在运行")
            .setContentText("离开页面后继续处理，点击停止可保留已生成内容。")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
    }

    private fun finishService() {
        stopping = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // -- Lifecycle Hooks

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ChannelId, "Agent 运行状态", NotificationManager.IMPORTANCE_LOW),
        )
        try {
            startForeground(NotificationId, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            active = true
        } catch (_: Exception) {
            timeline.interrupt("foreground_service_unavailable")
            finishService()
            return
        }
        serviceScope.launch {
            timeline.state.collect { state -> if (state.ready && !state.running) finishService() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == StopAction) {
            timeline.stop()
        } else if (!stopping) {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "XNote:AgentRun").apply {
                acquire(AgentRunLimits.MaxRunDurationMs + 15_000)
            }
        }
        // Process death is recovered by the persisted timeline, never by an automatic request replay.
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        timeline.interrupt("foreground_service_timeout")
        finishService()
    }

    override fun onDestroy() {
        active = false
        if (!stopping && timeline.state.value.running) timeline.interrupt("foreground_service_destroyed")
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
