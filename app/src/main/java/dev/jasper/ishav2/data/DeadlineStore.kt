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
    val turnedIn: Boolean = false,
    /** The linked task in the user's default Google Tasks list. */
    val taskId: String? = null,
    /** Done state both sides agreed on at the last Google Tasks sync. */
    val taskDone: Boolean? = null
) {
    val isDone: Boolean get() = done || turnedIn
    val fromClassroom: Boolean get() = source == SOURCE_CLASSROOM
    val fromTasks: Boolean get() = source == SOURCE_TASKS

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CLASSROOM = "classroom"
        const val SOURCE_TASKS = "tasks"
    }
}

/** A task read from Google Tasks. */
data class RemoteTask(
    val id: String,
    val title: String,
    val notes: String,
    val due: LocalDate?,
    val completed: Boolean,
    val link: String?
)

/** What a Google Tasks sync still has to send after merging the remote list. */
data class TaskPushes(
    /** Linked tasks whose done state changed on the phone: deadline id to task id and new state. */
    val status: List<Triple<String, String, Boolean>>,
    /** Deadlines that have no task yet. */
    val create: List<Deadline>
)

@Serializable
data class DeadlineData(
    val items: List<Deadline> = emptyList(),
    val lastSyncedAt: Long = 0,
    val lastSyncError: String? = null,
    /** Classroom items the user deleted, so a sync doesn't bring them back. */
    val dismissed: Set<String> = emptySet(),
    /** Deadlines whose task was deleted in Google Tasks: don't create it again. */
    val tasksSkip: Set<String> = emptySet(),
    /** Tasks to delete in Google Tasks once online (deleted on the phone). */
    val pendingTaskDeletes: Set<String> = emptySet(),
    val tasksSyncedAt: Long = 0,
    val tasksError: String? = null
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
                dismissed = if (found?.fromClassroom == true) data.dismissed + id else data.dismissed,
                pendingTaskDeletes = found?.taskId?.let { data.pendingTaskDeletes + it } ?: data.pendingTaskDeletes
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

    /** Merges a fresh Google Tasks list in and returns what still has to be pushed. */
    suspend fun mergeTasks(remote: List<RemoteTask>, at: Long = System.currentTimeMillis()): TaskPushes {
        var pushes = TaskPushes(emptyList(), emptyList())
        update { data ->
            val (next, todo) = reconcileTasks(data, remote, at)
            pushes = todo
            next
        }
        return pushes
    }

    /** Records that deadline [id] is now linked to [taskId] with the given done state on both sides. */
    suspend fun linkTask(id: String, taskId: String, done: Boolean) = update { data ->
        data.copy(items = data.items.map { if (it.id == id) it.copy(taskId = taskId, taskDone = done) else it })
    }

    suspend fun taskDeleted(taskId: String) = update { it.copy(pendingTaskDeletes = it.pendingTaskDeletes - taskId) }

    suspend fun tasksSynced(at: Long = System.currentTimeMillis()) = update { it.copy(tasksSyncedAt = at, tasksError = null) }

    suspend fun tasksFailed(message: String?) = update { it.copy(tasksError = message) }

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
                    if (old == null) item
                    else item.copy(done = old.done, notes = old.notes.ifBlank { item.notes }, taskId = old.taskId, taskDone = old.taskDone)
                }
            val manual = data.items.filter { !it.fromClassroom }
            // Old finished items only clutter the list.
            return (manual + classroom).filterNot { it.isDone && it.dueAt < now - KEEP_DONE_MS }
        }

        /** Items more overdue than this aren't pushed to Google Tasks for the first time. */
        private const val PUSH_LOOKBACK_MS = 3L * 24 * 60 * 60 * 1000

        private fun sameDay(a: Long, b: Long, zone: ZoneId): Boolean =
            Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

        /**
         * Pure two-way merge with Google Tasks. A change on one side since the last agreed
         * done state wins; the phone wins if both changed. Tasks with a due date come in as
         * deadlines, and open deadlines without a task are returned to be created.
         */
        fun reconcileTasks(
            data: DeadlineData,
            remote: List<RemoteTask>,
            now: Long,
            zone: ZoneId = ZoneId.systemDefault()
        ): Pair<DeadlineData, TaskPushes> {
            val byId = remote.associateBy { it.id }
            val linked = data.items.mapNotNull { it.taskId }.toMutableSet()
            linked += data.pendingTaskDeletes
            var skip = data.tasksSkip
            val status = mutableListOf<Triple<String, String, Boolean>>()
            val items = mutableListOf<Deadline>()

            for (item in data.items) {
                val taskId = item.taskId
                if (taskId == null) {
                    // Already in Google Tasks (same title and due day)? Link instead of duplicating.
                    val match = if (item.id in skip) null else remote.firstOrNull { r ->
                        r.id !in linked && r.due != null &&
                            r.title.trim().equals(item.title.trim(), ignoreCase = true) &&
                            endOfDay(r.due, zone).let { sameDay(it, item.dueAt, zone) }
                    }
                    if (match == null) { items += item; continue }
                    linked += match.id
                    val done = item.isDone || match.completed
                    if (done != match.completed) status += Triple(item.id, match.id, done)
                    items += item.copy(taskId = match.id, taskDone = match.completed, done = item.done || match.completed)
                    continue
                }
                val r = byId[taskId]
                if (r == null) {
                    // Deleted in Google Tasks: delete ours too, or just unlink an app/Classroom item.
                    if (!item.fromTasks) {
                        items += item.copy(taskId = null, taskDone = null)
                        skip = skip + item.id
                    }
                    continue
                }
                var next = item
                if (item.fromTasks) {
                    next = next.copy(
                        title = r.title.ifBlank { item.title },
                        notes = r.notes,
                        link = r.link ?: item.link,
                        dueAt = r.due?.let { endOfDay(it, zone) } ?: item.dueAt
                    )
                }
                val agreed = item.taskDone ?: r.completed
                val local = item.isDone
                next = when {
                    local != agreed -> {
                        if (r.completed != local) status += Triple(item.id, r.id, local)
                        next.copy(taskDone = if (r.completed == local) local else agreed)
                    }
                    r.completed != agreed -> next.copy(done = r.completed, taskDone = r.completed)
                    else -> next.copy(taskDone = agreed)
                }
                items += next
            }

            // New tasks with a due date become deadlines.
            for (r in remote) {
                if (r.id in linked || r.due == null) continue
                val dueAt = endOfDay(r.due, zone)
                if (r.completed && dueAt < now - KEEP_DONE_MS) continue
                items += Deadline(
                    id = "gt:${r.id}",
                    title = r.title.ifBlank { "Task" },
                    notes = r.notes,
                    dueAt = dueAt,
                    hasTime = false,
                    source = Deadline.SOURCE_TASKS,
                    link = r.link,
                    done = r.completed,
                    taskId = r.id,
                    taskDone = r.completed
                )
            }

            val create = items.filter {
                it.taskId == null && !it.isDone && !it.fromTasks && it.id !in skip && it.dueAt >= now - PUSH_LOOKBACK_MS
            }
            return data.copy(items = items, tasksSkip = skip) to TaskPushes(status, create)
        }

        /** What Google Tasks hasn't seen yet, as keys (empty when in sync). */
        fun tasksPending(data: DeadlineData, now: Long = System.currentTimeMillis()): Set<String> =
            data.pendingTaskDeletes.mapTo(mutableSetOf()) { "del:$it" } + data.items.filter { item ->
                if (item.taskId != null) item.taskDone != item.isDone
                else !item.isDone && !item.fromTasks && item.id !in data.tasksSkip && item.dueAt >= now - PUSH_LOOKBACK_MS
            }.map { "${it.id}:${it.isDone}" }

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
