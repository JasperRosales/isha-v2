package dev.jasper.ishav2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Something due: a Classroom assignment or one the user added by hand. */
@Serializable
data class Deadline(
    val id: String = "m:" + UUID.randomUUID().toString().take(8),
    val title: String,
    val course: String = "",
    /** Epoch ms. For date-only items this is 23:59 local on the due day. */
    val dueAt: Long,
    val hasTime: Boolean = true,
    val source: String = SOURCE_MANUAL,
    val link: String? = null,
    val notes: String = "",
    /** Checked off by the user. */
    val done: Boolean = false,
    /** Classroom says it's turned in or returned. */
    val turnedIn: Boolean = false
) {
    val isDone: Boolean get() = done || turnedIn
    val fromClassroom: Boolean get() = source == SOURCE_CLASSROOM

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CLASSROOM = "classroom"
    }
}

@Serializable
data class DeadlineData(
    val items: List<Deadline> = emptyList(),
    val lastSyncedAt: Long = 0,
    val lastSyncError: String? = null,
    /** Classroom items the user deleted, so a sync doesn't bring them back. */
    val dismissed: Set<String> = emptySet()
)

enum class DeadlineGroup(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    THIS_WEEK("This week"),
    LATER("Later"),
    DONE("Done")
}

/** Deadlines kept on the phone: the Due screen, reminders and isha all read from here, online or not. */
class DeadlineStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _data = MutableStateFlow(read())
    val data: StateFlow<DeadlineData> = _data.asStateFlow()

    val current: DeadlineData get() = _data.value

    suspend fun add(deadline: Deadline) = update { data ->
        data.copy(items = data.items.filterNot { it.id == deadline.id } + deadline)
    }

    suspend fun setDone(id: String, done: Boolean) = update { data ->
        data.copy(items = data.items.map { if (it.id == id) it.copy(done = done) else it })
    }

    suspend fun delete(id: String): Deadline? {
        val found = _data.value.items.firstOrNull { it.id == id }
        update { data ->
            data.copy(
                items = data.items.filterNot { it.id == id },
                dismissed = if (found?.fromClassroom == true) data.dismissed + id else data.dismissed
            )
        }
        return found
    }

    /** Replaces the Classroom items with a fresh fetch, keeping what the user set locally. */
    suspend fun mergeClassroom(fetched: List<Deadline>, at: Long = System.currentTimeMillis()) = update { data ->
        data.copy(
            items = merge(data, fetched, at),
            lastSyncedAt = at,
            lastSyncError = null
        )
    }

    suspend fun syncFailed(message: String) = update { it.copy(lastSyncError = message) }

    private suspend fun update(transform: (DeadlineData) -> DeadlineData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_data.value)
            write(next)
            _data.value = next
        }
    }

    private fun read(): DeadlineData {
        if (!file.exists()) return DeadlineData()
        return runCatching { json.decodeFromString<DeadlineData>(file.readText()) }.getOrDefault(DeadlineData())
    }

    private fun write(data: DeadlineData) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(data))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(data))
            temp.delete()
        }
    }

    companion object {
        private const val KEEP_DONE_MS = 30L * 24 * 60 * 60 * 1000

        /** Pure merge, kept separate for tests. */
        fun merge(data: DeadlineData, fetched: List<Deadline>, now: Long): List<Deadline> {
            val previous = data.items.associateBy { it.id }
            val classroom = fetched
                .filterNot { it.id in data.dismissed }
                .distinctBy { it.id }
                .map { item ->
                    val old = previous[item.id]
                    if (old == null) item else item.copy(done = old.done, notes = old.notes.ifBlank { item.notes })
                }
            val manual = data.items.filter { !it.fromClassroom }
            // Old finished items only clutter the list.
            return (manual + classroom).filterNot { it.isDone && it.dueAt < now - KEEP_DONE_MS }
        }

        fun groupOf(item: Deadline, now: ZonedDateTime = ZonedDateTime.now()): DeadlineGroup {
            if (item.isDone) return DeadlineGroup.DONE
            val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.dueAt), now.zone)
            val today = now.toLocalDate()
            val dueDay = due.toLocalDate()
            return when {
                due.isBefore(now) -> DeadlineGroup.OVERDUE
                dueDay == today -> DeadlineGroup.TODAY
                dueDay == today.plusDays(1) -> DeadlineGroup.TOMORROW
                dueDay.isBefore(today.plusDays(7)) -> DeadlineGroup.THIS_WEEK
                else -> DeadlineGroup.LATER
            }
        }

        /** Groups in display order, each sorted soonest first (Done: most recent first). */
        fun grouped(items: List<Deadline>, now: ZonedDateTime = ZonedDateTime.now()): List<Pair<DeadlineGroup, List<Deadline>>> =
            items.groupBy { groupOf(it, now) }
                .toSortedMap(compareBy { it.ordinal })
                .map { (group, list) ->
                    group to if (group == DeadlineGroup.DONE) list.sortedByDescending { it.dueAt } else list.sortedBy { it.dueAt }
                }

        fun endOfDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
            date.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        /** "Thu, Nov 5 · 11:59 PM", or just the date for date-only items. */
        fun describeDue(item: Deadline, zone: ZoneId = ZoneId.systemDefault()): String {
            val due = ZonedDateTime.ofInstant(Instant.ofEpochMilli(item.dueAt), zone)
            val day = due.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
            return if (item.hasTime) "$day · ${due.format(DateTimeFormatter.ofPattern("h:mm a"))}" else day
        }

        /** Compact text for prompts (offline brain, briefing, tools). */
        fun snapshotForPrompt(items: List<Deadline>, now: ZonedDateTime = ZonedDateTime.now(), limit: Int = 15): String {
            val open = grouped(items, now).filter { it.first != DeadlineGroup.DONE }
            if (open.isEmpty()) return "Deadlines saved on the phone: none open."
            return buildString {
                appendLine("Deadlines saved on the phone (open only):")
                var count = 0
                for ((group, list) in open) {
                    for (item in list) {
                        if (count++ >= limit) break
                        val course = if (item.course.isNotBlank()) " (${item.course})" else ""
                        appendLine("- [${group.label}] ${item.title}$course, due ${describeDue(item, now.zone)}")
                    }
                }
            }.trimEnd()
        }
    }
}
