package dev.jasper.ishav2.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.jasper.ishav2.data.ConversationStore
import dev.jasper.ishav2.data.BudgetStore
import dev.jasper.ishav2.data.MemoryStore
import dev.jasper.ishav2.data.NotificationStore
import dev.jasper.ishav2.data.ReminderStore
import dev.jasper.ishav2.local.LocalLlm
import dev.jasper.ishav2.local.LocalModelManager
import dev.jasper.ishav2.notify.ReminderReceiver
import dev.jasper.ishav2.util.NetworkMonitor
import dev.jasper.ishav2.worker.BriefingWorker
import dev.jasper.ishav2.data.SettingsStore
import dev.jasper.ishav2.net.ComposioManager
import dev.jasper.ishav2.net.ComposioMcp
import dev.jasper.ishav2.net.ComposioRest
import dev.jasper.ishav2.net.GeminiClient
import dev.jasper.ishav2.util.FileStore
import dev.jasper.ishav2.util.Notifier
import dev.jasper.ishav2.worker.MailClassroomWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/** One long-lived graph for the whole app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val settings = SettingsStore(appContext)
    val conversations = ConversationStore(File(appContext.filesDir, "chats"))
    val notifications = NotificationStore(File(appContext.filesDir, "notifications.json"))
    val notifier = Notifier(appContext)
    val memory = MemoryStore(File(appContext.filesDir, "memory.json"))
    val reminders = ReminderStore(File(appContext.filesDir, "reminders.json"))
    val budget = BudgetStore(File(appContext.filesDir, "budget.json"))
    val network = NetworkMonitor(appContext)
    val models = LocalModelManager(appContext, settings)
    val localLlm = LocalLlm(appContext)
    val approvals = ApprovalBroker()
    val gemini = GeminiClient(settings)
    val composioRest = ComposioRest(settings)
    val composioMcp = ComposioMcp(settings)
    val composio = ComposioManager(settings, composioRest, composioMcp)
    val budgetAssistant = BudgetAssistant(budget, notifier)
    val nativeTools = NativeTools(appContext, notifications, notifier, memory, reminders, budget, budgetAssistant)
    val runner = AgentRunner(
        settings, gemini, composio, nativeTools, approvals,
        memory, models, localLlm, network, budgetAssistant
    )

    /** App-scoped chat: the agent run outlives every screen and ViewModel. */
    val chat = ChatSession(settings, conversations, composio, runner, approvals)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        FileStore.attachmentsDir(appContext)
        notifier.ensureChannels()
        scheduleMailPolling(appContext)
        BriefingWorker.schedule(appContext, settings.current, ExistingWorkPolicy.KEEP)
        // Alarms do not survive force-stops; re-arm pending reminders on launch.
        ReminderReceiver.rearmAll(appContext, reminders.upcoming())
        if (settings.current.hasComposioKey) {
            scope.launch { runCatching { composio.ensure() } }
        }
        // Pin the process while the assistant is generating, so replies finish
        // even when the app is backgrounded (see ChatService).
        scope.launch {
            chat.busy.collect { busy -> if (busy) ChatService.start(appContext) }
        }
    }

    private fun scheduleMailPolling(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MailClassroomWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "isha_mail_classroom_poll",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
