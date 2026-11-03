package dev.jasper.ishav2.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.net.GmailSync
import dev.jasper.ishav2.util.Notifier

/**
 * Every 30 minutes (Settings → Gmail new mail / Google Classroom): pushes new
 * unread mail and new Classroom announcements. Uses the same Composio calls as
 * the Due screen and mail digest, so it works with either key type.
 */
class MailClassroomWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? IshaApp)?.container ?: return Result.success()
        val current = container.settings.current
        if (!current.hasComposioKey) return Result.success()
        if (current.gmailPollingEnabled) runCatching { checkGmail(container) }
        if (current.classroomPollingEnabled) runCatching { checkClassroom(container) }
        return Result.success()
    }

    private suspend fun checkGmail(container: AppContainer) {
        val settings = container.settings
        val last = settings.current.gmailLastCheckedAt
        // First run: start from now-ish instead of flooding with a day of mail.
        val since = if (last == 0L) System.currentTimeMillis() - FIRST_RUN_LOOKBACK_MS else last
        val fresh = container.gmail.fetch(GmailSync.UNREAD_QUERY, max = 15)
            .filter { it.receivedAt > since && it.id != settings.current.gmailLastMessageId }
        if (fresh.isEmpty()) {
            if (last == 0L) settings.setGmailLastChecked(since, settings.current.gmailLastMessageId)
            return
        }
        for (mail in fresh.take(MAX_ALERTS).reversed()) {
            val title = "Gmail: ${mail.subject}"
            val message = listOf("From ${mail.from}".takeIf { mail.from.isNotBlank() }, mail.snippet.takeIf { it.isNotBlank() })
                .filterNotNull()
                .joinToString("\n")
                .ifBlank { "New unread message" }
            container.notifications.add(InboxItem(title = title, message = message, source = "gmail"))
            Notifier.postLocal(container.appContext, title, message.take(160))
        }
        if (fresh.size > MAX_ALERTS) {
            val more = fresh.size - MAX_ALERTS
            container.notifications.add(
                InboxItem(title = "Gmail", message = "…and $more more unread. See the Mail digest.", source = "gmail")
            )
        }
        val newest = fresh.maxBy { it.receivedAt }
        settings.setGmailLastChecked(newest.receivedAt, newest.id)
    }

    private suspend fun checkClassroom(container: AppContainer) {
        val settings = container.settings
        val last = settings.current.classroomLastCheckedAt
        val since = if (last == 0L) System.currentTimeMillis() - FIRST_RUN_LOOKBACK_MS else last
        val fresh = container.classroom.fetchAnnouncements(since)
            .filter { it.id != settings.current.classroomLastActivityId }
        if (fresh.isEmpty()) {
            if (last == 0L) settings.setClassroomLastChecked(since, settings.current.classroomLastActivityId)
            return
        }
        for (post in fresh.take(MAX_ALERTS).reversed()) {
            val title = "Classroom: ${post.course}"
            container.notifications.add(InboxItem(title = title, message = post.text.take(400), source = "classroom"))
            Notifier.postLocal(container.appContext, title, post.text.take(160))
        }
        val newest = fresh.maxBy { it.createdAt }
        settings.setClassroomLastChecked(newest.createdAt, newest.id)
        // A new post often means new work: refresh the Due list too.
        container.sync.syncDeadlines()
    }

    private companion object {
        const val MAX_ALERTS = 5
        const val FIRST_RUN_LOOKBACK_MS = 30L * 60 * 1000
    }
}
