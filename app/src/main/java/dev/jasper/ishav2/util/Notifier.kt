package dev.jasper.ishav2.util

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import dev.jasper.ishav2.MainActivity
import dev.jasper.ishav2.R
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.notify.ReplyReceiver

/**
 * Every notification isha posts. Channels are split by how much they should
 * interrupt: replies and reminders make noise, background work stays silent.
 */
class Notifier(private val context: Context) {

    private val avatar: Bitmap? by lazy {
        runCatching {
            ContextCompat.getDrawable(context, R.drawable.isha_avatar)?.toBitmap(192, 192)
        }.getOrNull()
    }

    private val isha: Person by lazy {
        Person.Builder()
            .setName("isha")
            .setKey("isha")
            .setBot(true)
            .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
            .build()
    }

    private val me: Person = Person.Builder().setName("You").setKey("me").build()

    fun ensureChannel() = ensureChannels()

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.notification_channel_desc) },
                NotificationChannel(
                    CHANNEL_REPLIES,
                    context.getString(R.string.chat_channel_replies),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.chat_channel_replies_desc) },
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.reminder_channel_desc) },
                NotificationChannel(
                    CHANNEL_BRIEFING,
                    context.getString(R.string.briefing_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.briefing_channel_desc) },
                NotificationChannel(
                    CHANNEL_BUDGET,
                    context.getString(R.string.budget_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.budget_channel_desc) },
                NotificationChannel(
                    CHANNEL_WORK,
                    context.getString(R.string.chat_channel_work),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.chat_channel_work_desc)
                    setSound(null, null)
                },
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    context.getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.download_channel_desc)
                    setSound(null, null)
                }
            )
        )
    }

    /** An inbox item (from a tool or a poller), opening the inbox on tap. */
    fun post(item: InboxItem) {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatar)
            .setColor(ACCENT)
            .setContentTitle(item.title.ifBlank { "isha" })
            .setContentText(item.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
            .setContentIntent(inboxIntent(item.id.hashCode()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notify(item.id.hashCode(), notification)
    }

    /**
     * A finished reply, shown as a chat bubble from isha with an inline Reply
     * action, so a conversation can continue straight from the shade.
     */
    fun postReply(conversationId: String, title: String, userText: String?, reply: String) {
        if (!canPost()) return
        ensureChannels()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title.ifBlank { "isha" })
        if (!userText.isNullOrBlank()) {
            style.addMessage(userText.take(CLIP), System.currentTimeMillis() - 1, me)
        }
        style.addMessage(reply.take(CLIP), System.currentTimeMillis(), isha)
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setStyle(style)
            .setContentIntent(chatIntent(conversationId))
            .addAction(replyAction(conversationId))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notify(replyId(conversationId), notification)
    }

    /** Swaps the reply notification for a quiet "thinking" state after an inline reply. */
    fun postReplyPending(conversationId: String, title: String, userText: String) {
        if (!canPost()) return
        ensureChannels()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(title.ifBlank { "isha" })
            .addMessage(userText.take(CLIP), System.currentTimeMillis(), me)
        val notification = NotificationCompat.Builder(context, CHANNEL_REPLIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setStyle(style)
            .setSubText("isha is typing…")
            .setContentIntent(chatIntent(conversationId))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notify(replyId(conversationId), notification)
    }

    fun cancelReply(conversationId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(replyId(conversationId)) }
    }

    fun postReminder(id: Int, title: String, message: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatar)
            .setColor(ACCENT)
            .setContentTitle(title.ifBlank { "Reminder" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(inboxIntent(id))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notify(id, notification)
    }

    fun postBriefing(conversationId: String, title: String, summary: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatar)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.take(CLIP)))
            .setContentIntent(chatIntent(conversationId))
            .addAction(replyAction(conversationId))
            .setAutoCancel(true)
            .build()
        notify(replyId(conversationId), notification)
    }

    fun postBudgetAlert(title: String, message: String) {
        if (!canPost()) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_BUDGET, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            BUDGET_NOTIF_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatar)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        notify(BUDGET_NOTIF_ID + (title.hashCode() and 0xFF), notification)
    }

    fun workNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_WORK)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(context.getString(R.string.chat_work_title))
            .setContentText(context.getString(R.string.chat_work_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(chatIntent(null))
            .build()
    }

    fun downloadNotification(title: String, done: Long, total: Long): Notification {
        ensureChannels()
        val percent = if (total > 0) ((done * 100) / total).toInt() else 0
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(ACCENT)
            .setContentTitle("Downloading $title")
            .setContentText(
                if (total > 0) "${humanSize(done)} of ${humanSize(total)} · $percent%"
                else humanSize(done)
            )
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(modelsIntent())
            .build()
    }

    fun postDownloadResult(title: String, ok: Boolean, message: String) {
        if (!canPost()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setColor(ACCENT)
            .setContentTitle(if (ok) "$title is ready offline" else "$title download failed")
            .setContentText(message)
            .setContentIntent(modelsIntent())
            .setAutoCancel(true)
            .build()
        notify(DOWNLOAD_RESULT_ID, notification)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun replyAction(conversationId: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(ReplyReceiver.KEY_TEXT)
            .setLabel("Reply to isha")
            .build()
        val intent = Intent(context, ReplyReceiver::class.java).apply {
            action = ReplyReceiver.ACTION_REPLY
            putExtra(ReplyReceiver.EXTRA_CONVERSATION_ID, conversationId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            replyId(conversationId),
            intent,
            // RemoteInput needs a mutable PendingIntent to receive the typed text.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notification, "Reply", pending)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun chatIntent(conversationId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CHAT, true)
            if (conversationId != null) putExtra(MainActivity.EXTRA_CONVERSATION_ID, conversationId)
        }
        return PendingIntent.getActivity(
            context,
            conversationId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun inboxIntent(requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_INBOX, true)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun modelsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_MODELS, true)
        }
        return PendingIntent.getActivity(
            context,
            DOWNLOAD_RESULT_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun replyId(conversationId: String): Int = REPLY_BASE + (conversationId.hashCode() and 0xFFFF)

    companion object {
        const val CHANNEL_ID = "isha"
        const val CHANNEL_REPLIES = "isha_replies_v2"
        const val CHANNEL_REMINDERS = "isha_reminders"
        const val CHANNEL_BRIEFING = "isha_briefing"
        const val CHANNEL_WORK = "isha_work"
        const val CHANNEL_DOWNLOADS = "isha_downloads"
        const val CHANNEL_BUDGET = "isha_budget"
        private const val BUDGET_NOTIF_ID = 4200
        const val WORK_NOTIF_ID = 4101
        const val DOWNLOAD_NOTIF_ID = 4103
        private const val DOWNLOAD_RESULT_ID = 4104
        private const val REPLY_BASE = 0x50000
        private const val CLIP = 1500
        private const val ACCENT = 0xFFFF6D1F.toInt()

        /** True while any isha activity is on screen. */
        fun isAppVisible(): Boolean {
            val info = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(info)
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }

        fun postLocal(context: Context, title: String, message: String) {
            Notifier(context).post(InboxItem(title = title, message = message))
        }
    }
}
