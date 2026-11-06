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
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.data.MailDigestStore
import dev.jasper.ishav2.net.ClassroomSync
import dev.jasper.ishav2.net.GoogleTasksSync
import dev.jasper.ishav2.net.ComposioCalls
import dev.jasper.ishav2.net.GmailSync
import dev.jasper.ishav2.notify.DeadlineReceiver
import dev.jasper.ishav2.worker.SyncWorker
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
import dev.jasper.ishav2.net.ExtraClient
import dev.jasper.ishav2.net.GroqClient
import dev.jasper.ishav2.net.GeminiClient
import dev.jasper.ishav2.util.FileStore
import dev.jasper.ishav2.util.Notifier
import dev.jasper.ishav2.worker.MailClassroomWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dev.jasper.ishav2.widget.IshaWidget
import dev.jasper.ishav2.data.ExamStore
import dev.jasper.ishav2.exam.ExamGenerator
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
    val deadlines = DeadlineStore(File(appContext.filesDir, "deadlines.json"))
    val mailDigest = MailDigestStore(File(appContext.filesDir, "mail_digest.json"))
    val network = NetworkMonitor(appContext)
    val models = LocalModelManager(appContext, settings)
    val localLlm = LocalLlm(appContext)
    val approvals = ApprovalBroker()
    val gemini = GeminiClient(settings)
    val groq = GroqClient(settings)
    /** Optional third brain (Cerebras, OpenRouter, …). */
    val extra = ExtraClient(settings)
    /** Practice exams written from the student's own notes. */
    val exams = ExamStore(File(appContext.filesDir, "exams.json"))
    val examGenerator = ExamGenerator(settings, groq, gemini)
    val composioRest = ComposioRest(settings)
    val composioMcp = ComposioMcp(settings)
    val composio = ComposioManager(settings, composioRest, composioMcp)
    val budgetAssistant = BudgetAssistant(budget, notifier)
    val nativeTools = NativeTools(
        appContext, notifications, notifier, memory, reminders, budget, budgetAssistant,
        deadlines, mailDigest
    )
    val runner = AgentRunner(
        settings, groq, gemini, extra, composio, nativeTools, approvals,
        memory, models, localLlm, network, budgetAssistant, deadlines, mailDigest
    )

    /** App-scoped chat: the agent run outlives every screen and ViewModel. */
    val chat = ChatSession(settings, conversations, composio, runner, approvals)

    private val composioCalls = ComposioCalls(settings, composio)
    val classroom = ClassroomSync(composioCalls)
    val gmail = GmailSync(composioCalls)
    val googleTasks = GoogleTasksSync(composioCalls)

    /** Classroom deadlines + Gmail digest, saved on the phone for offline use. */
    val sync = SyncCenter(settings, network, deadlines, mailDigest, classroom, gmail, googleTasks) { chat }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        FileStore.attachmentsDir(appContext)
        notifier.ensureChannels()
        // Scheduling and alarm re-arming are binder calls and disk work: keep them off
        // the main thread so the first frame (and the splash) isn't held up.
        scope.launch {
            scheduleMailPolling(appContext)
            BriefingWorker.schedule(appContext, settings.current, ExistingWorkPolicy.KEEP)
            SyncWorker.schedule(appContext)
            // Alarms do not survive force-stops; re-arm pending reminders on launch.
            ReminderReceiver.rearmAll(appContext, reminders.upcoming())
        }
        // Deadline heads-ups follow the saved list: re-armed on launch (alarms
        // don't survive reboots or force-stops) and on every change.
        scope.launch {
            deadlines.data.collect { DeadlineReceiver.sync(appContext, it.items) }
        }
        // Push changes made on the phone (added, ticked off, deleted) to Google Tasks
        // shortly after they happen. Keyed on what's pending, so a failed push
        // doesn't retry in a loop; the periodic sync picks it up later.
        scope.launch {
            deadlines.data
                .map { DeadlineStore.tasksPending(it) }
                .distinctUntilChanged()
                .collectLatest { pending ->
                    if (pending.isEmpty() || !settings.current.tasksSyncEnabled || !network.isOnline()) return@collectLatest
                    delay(1_500)
                    sync.syncTasks()
                }
        }
        if (settings.current.hasComposioKey) {
            scope.launch { runCatching { composio.ensure() } }
        }
        // The home-screen widget mirrors deadlines, budget and the theme.
        scope.launch {
            combine(deadlines.data, budget.data, settings.settings.map { it.darkTheme }.distinctUntilChanged()) { _, _, _ -> }
                .collectLatest {
                    delay(300) // coalesce bursts, e.g. a Classroom sync
                    runCatching { IshaWidget.update(appContext) }
                }
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
