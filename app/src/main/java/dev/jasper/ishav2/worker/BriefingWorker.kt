package dev.jasper.ishav2.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.data.MessageStatus
import dev.jasper.ishav2.data.Role
import dev.jasper.ishav2.data.plainText
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * The daily briefing: once a day, isha checks the user's calendar, inbox and
 * tasks through Composio and drops a short summary into the inbox (no new
 * chat). The notification opens the inbox.
 */
class BriefingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IshaApp).container
        val settings = container.settings.current
        if (!settings.briefingEnabled) return Result.success()

        try {
            if (settings.hasComposioKey) runCatching { container.composio.ensure(force = false) }
            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            val saved = DeadlineStore.snapshotForPrompt(container.deadlines.current.items)
            val conversation = container.chat.runInBackground(
                title = "Briefing · $today",
                prompt = "$PROMPT\n\nFor tasks and assignments, start from these (already on the phone):\n$saved"
            )
            val reply = conversation.messages.lastOrNull { it.role == Role.MODEL }
            val text = reply?.takeIf { it.status == MessageStatus.DONE }?.plainText()?.trim().orEmpty()
            val item = if (text.isNotBlank()) {
                InboxItem(title = "Briefing · $today", message = text, source = SOURCE)
            } else {
                InboxItem(
                    title = "Briefing · $today",
                    message = "isha couldn't put today's briefing together. Check your connection and API keys in Settings.",
                    level = "warning",
                    source = SOURCE
                )
            }
            container.notifications.add(item)
            container.notifier.postBriefing(item)
        } finally {
            // Always line up tomorrow's run, even if today's failed. A manual
            // "send one now" leaves the daily schedule alone.
            if (!inputData.getBoolean(KEY_MANUAL, false)) {
                schedule(applicationContext, container.settings.current, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "isha_daily_briefing"
        private const val KEY_MANUAL = "manual"
        const val SOURCE = "briefing"

        private val PROMPT = """
            Good morning! Give me my daily briefing for today. Check, in one batch where you can:
            1. Today's calendar events (time, title, where).
            2. Unread or important emails from the last 24 hours: who, what they want, and whether I need to act.
            3. Tasks or assignments due today or overdue, if a tasks/classroom app is connected.
            4. My scheduled reminders.
            Lead with anything that needs action. Keep it short and skimmable. Skip any app that isn't connected without explaining at length.
        """.trimIndent()

        /**
         * Queues the next briefing. REPLACE applies a changed time; the worker
         * itself APPENDs (REPLACE would cancel the run that is finishing), and
         * app launch uses KEEP so it never disturbs a run in progress.
         */
        fun schedule(
            context: Context,
            settings: AppSettings,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val manager = WorkManager.getInstance(context)
            if (!settings.briefingEnabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = ZonedDateTime.now()
            var next = now.withHour(settings.briefingHour)
                .withMinute(settings.briefingMinute)
                .withSecond(0)
                .withNano(0)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            val request = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            manager.enqueueUniqueWork(WORK_NAME, policy, request)
        }

        /** Runs a briefing right now (from Settings), without changing the schedule. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BriefingWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("$WORK_NAME-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
