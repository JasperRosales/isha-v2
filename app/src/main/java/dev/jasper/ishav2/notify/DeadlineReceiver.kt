package dev.jasper.ishav2.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.DeadlineStore
import dev.jasper.ishav2.data.InboxItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deadline heads-ups, scheduled on the phone so they fire with no internet.
 * Alarms are derived from the saved deadlines; on fire the receiver re-checks
 * the item, so a stale alarm for something done, moved or deleted stays quiet.
 */
class DeadlineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val container = (context.applicationContext as IshaApp).container
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val dueAt = intent.getLongExtra(EXTRA_DUE, 0L)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val item = container.deadlines.current.items.firstOrNull { it.id == id } ?: return@launch
                if (item.isDone || item.dueAt != dueAt) return@launch
                val title = when (kind) {
                    KIND_SOON -> "Due in 2 hours"
                    KIND_MORNING -> "Due today"
                    else -> "Due tomorrow"
                }
                val course = if (item.course.isNotBlank()) " · ${item.course}" else ""
                val message = "${item.title}$course\nDue ${DeadlineStore.describeDue(item)}"
                container.notifications.add(
                    InboxItem(title = title, message = message, level = "warning", source = SOURCE)
                )
                container.notifier.postReminder(requestCode(id, kind), title, message)
            } finally {
                pending.finish()
            }
        }
    }

    data class Alarm(val id: String, val kind: String, val at: Long, val dueAt: Long)

    companion object {
        const val SOURCE = "deadline"
        private const val ACTION_FIRE = "dev.jasper.ishav2.action.DEADLINE"
        private const val EXTRA_ID = "deadline_id"
        private const val EXTRA_DUE = "deadline_due"
        private const val EXTRA_KIND = "deadline_kind"
        private const val KIND_DAY_BEFORE = "day"
        private const val KIND_SOON = "soon"
        private const val KIND_MORNING = "morning"
        private const val HOUR = 60L * 60 * 1000
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Every heads-up that should be pending right now for [items]. Pure, for tests. */
        fun plan(items: List<Deadline>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Alarm> =
            items.filter { !it.isDone && it.dueAt > now }.flatMap { item ->
                val times = if (item.hasTime) {
                    listOf(KIND_DAY_BEFORE to item.dueAt - 24 * HOUR, KIND_SOON to item.dueAt - 2 * HOUR)
                } else {
                    // Date-only: an evening nudge the day before and a morning one on the day.
                    val day = ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.dueAt), zone).toLocalDate()
                    listOf(
                        KIND_DAY_BEFORE to day.minusDays(1).atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
                        KIND_MORNING to day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
                    )
                }
                times.filter { (_, at) -> at > now }.map { (kind, at) -> Alarm(item.id, kind, at, item.dueAt) }
            }

        private val armed = mutableSetOf<Pair<String, String>>()

        /** Brings the scheduled alarms in line with [items]; called on every change. */
        @Synchronized
        fun sync(context: Context, items: List<Deadline>) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val wanted = plan(items, System.currentTimeMillis())
            val wantedKeys = wanted.map { it.id to it.kind }.toSet()
            (armed - wantedKeys).forEach { (id, kind) -> alarms.cancel(fireIntent(context, id, kind, 0L)) }
            armed.clear()
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
            for (alarm in wanted) {
                val operation = fireIntent(context, alarm.id, alarm.kind, alarm.dueAt)
                if (exact) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.at, operation)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.at, operation)
                }
                armed += alarm.id to alarm.kind
            }
        }

        private fun requestCode(id: String, kind: String): Int = "deadline:$id:$kind".hashCode()

        private fun fireIntent(context: Context, id: String, kind: String, dueAt: Long): PendingIntent {
            val intent = Intent(context, DeadlineReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_KIND, kind)
                putExtra(EXTRA_DUE, dueAt)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(id, kind),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
