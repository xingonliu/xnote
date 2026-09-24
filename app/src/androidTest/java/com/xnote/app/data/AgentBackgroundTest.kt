package com.xnote.app.data

import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.xnote.app.MainActivity
import com.xnote.app.XNoteApplication
import com.xnote.app.data.agent.AgentRunService
import com.xnote.app.domain.agent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

// -- Tests

class AgentBackgroundTest {
    @Test fun foregroundNotificationSurvivesHomeAndScreenOffAndStopsTheRun() = runBlocking { checkBackground(false) }

    @Test fun deniedNotificationsKeepForegroundExecutionAndInAppStopAvailable() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<XNoteApplication>()
        org.junit.Assume.assumeFalse("Revoke POST_NOTIFICATIONS before starting instrumentation; revoking inside a test kills its process.",
            app.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
        checkBackground(true)
    }

    // -- Functions

    private suspend fun checkBackground(denyNotifications: Boolean) {
        val application = ApplicationProvider.getApplicationContext<XNoteApplication>()
        val timeline = application.container.agentTimeline
        val profiles = application.container.modelProfiles
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        timeline.awaitReady()
        timeline.clearChat()
        val profile = ModelProfile("background-test", name = "后台测试", protocol = ModelProtocol.OpenAI,
            baseUrl = "https://192.0.2.1/v1", modelId = "test", isDefault = true)
        profiles.save(profile, "local-background-test")
        if (!denyNotifications) instrumentation.uiAutomation.grantRuntimePermission(application.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // TEST-NET-1 provides a pending, bounded request without sending real user data or credentials.
            scenario.onActivity { runBlocking { timeline.send("后台生命周期测试") } }
            val manager = application.getSystemService(NotificationManager::class.java)
            val notification = if (denyNotifications) null else withTimeout(5000) {
                while (manager.activeNotifications.none { it.notification.channelId == "agent_execution" }) delay(50)
                manager.activeNotifications.single { it.notification.channelId == "agent_execution" }.notification
            }
            if (denyNotifications) assertFalse(manager.areNotificationsEnabled())
            withTimeout(5000) {
                @Suppress("DEPRECATION")
                while (application.getSystemService(android.app.ActivityManager::class.java).getRunningServices(20).none {
                    it.service.className == AgentRunService::class.java.name && it.foreground
                }) delay(50)
            }
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            delay(500)
            assertTrue(timeline.state.value.running)
            if (notification != null) {
                assertTrue(manager.activeNotifications.any { it.notification.channelId == "agent_execution" })
                notification.actions.single { it.title == "停止" }.actionIntent.send()
            } else timeline.stop()
            withTimeout(5000) { timeline.state.first { !it.running } }
            assertTrue(application.container.database.agent().unfinishedRuns().isEmpty())
            assertEquals(AgentRunStatus.Cancelled, application.container.database.agent().run(
                application.container.database.agent().messages().first { it.role == AgentMessageRole.User }.runId!!)?.status)
        } finally {
            instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
            timeline.clearChat()
            scenario.close()
            profiles.delete(profile.id)
        }
    }
}
