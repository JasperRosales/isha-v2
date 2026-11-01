package dev.jasper.ishav2.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires reminders isha scheduled, and re-arms the remaining ones after a
 * reboot or app update (Android drops every alarm on both).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as IshaApp).container
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        val id = intent.getStringExtra(EXTRA_ID) ?: return@launch
                        val reminder = container.reminders.remove(id) ?: return@launch
                        container.notifications.add(
                            InboxItem(
                                title = reminder.title,
                                message = reminder.message,
                                level = "info",
                                source = "reminder"
                            )
                        )
                        container.notifier.postReminder(id.hashCode(), reminder.title, reminder.message)
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> rearmAll(context, container.reminders.upcoming())
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_FIRE = "dev.jasper.ishav2.action.REMINDER"
        private const val EXTRA_ID = "reminder_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun schedule(context: Context, reminder: Reminder) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val at = reminder.at.coerceAtLeast(System.currentTimeMillis() + 1_000)
            val operation = fireIntent(context, reminder.id)
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        }

        fun cancel(context: Context, id: String) {
            context.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(context, id))
        }

        fun rearmAll(context: Context, reminders: List<Reminder>) {
            reminders.forEach { schedule(context, it) }
        }

        private fun fireIntent(context: Context, id: String): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_ID, id)
            }
            return PendingIntent.getBroadcast(
                context,
                id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
