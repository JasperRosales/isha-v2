package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.data.RemoteTask
import dev.jasper.ishav2.net.ComposioJson.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads and writes the user's default Google Tasks list through Composio. */
class GoogleTasksSync(private val calls: ComposioCalls) {

    suspend fun fetchAll(): List<RemoteTask> {
        val out = mutableListOf<RemoteTask>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val page = calls.run(
                LIST,
                buildJsonObject {
                    put("tasklist_id", LIST_ID)
                    put("maxResults", 100)
                    put("showCompleted", true)
                    // Completed tasks are hidden unless this is set.
                    put("showHidden", true)
                    token?.let { put("pageToken", it) }
                }
            )
            out += parseTasks(page)
            token = findString(page, "nextPageToken") ?: return out
        }
        return out
    }

    /** Creates a task for [item] and returns its id. */
    suspend fun insert(item: Deadline, zone: ZoneId = ZoneId.systemDefault()): String {
        val notes = listOfNotNull(
            item.course.takeIf { it.isNotBlank() },
            item.notes.takeIf { it.isNotBlank() },
            item.link
        ).joinToString("\n")
        val result = calls.run(
            INSERT,
            buildJsonObject {
                put("tasklist_id", LIST_ID)
                put("title", item.title.take(1024))
                if (notes.isNotBlank()) put("notes", notes.take(8000))
                put("due", dueString(Instant.ofEpochMilli(item.dueAt).atZone(zone).toLocalDate()))
                put("status", if (item.isDone) "completed" else "needsAction")
            }
        )
        return findTask(result)?.str("id") ?: throw ApiException(500, "Google Tasks didn't return the new task.")
    }

    suspend fun setDone(taskId: String, done: Boolean) {
        calls.run(
            PATCH,
            buildJsonObject {
                put("tasklist_id", LIST_ID)
                put("task_id", taskId)
                put("status", if (done) "completed" else "needsAction")
            }
        )
    }

    /** @return true if it's gone (deleted now, or already missing). */
    suspend fun delete(taskId: String): Boolean = runCatching {
        calls.run(
            DELETE,
            buildJsonObject {
                put("tasklist_id", LIST_ID)
                put("task_id", taskId)
            }
        )
        true
    }.getOrElse { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        val msg = error.message.orEmpty()
        msg.contains("404") || msg.contains("not found", true) || msg.contains("deleted", true)
    }

    companion object {
        private const val LIST_ID = "@default"
        private const val MAX_PAGES = 10

        val LIST = listOf("GOOGLETASKS_LIST_TASKS")
        val INSERT = listOf("GOOGLETASKS_INSERT_TASK")
        val PATCH = listOf("GOOGLETASKS_PATCH_TASK")
        val DELETE = listOf("GOOGLETASKS_DELETE_TASK")

        /** Google Tasks keeps only the date; send it as midnight UTC of that day. */
        fun dueString(date: LocalDate): String = "${date}T00:00:00.000Z"

        fun parseTasks(element: JsonElement): List<RemoteTask> =
            ComposioJson.findArray(element, setOf("tasks", "items")).orEmpty().mapNotNull { node ->
                val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                val id = obj.str("id") ?: return@mapNotNull null
                if ((obj["deleted"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                // Subtasks stay in Google Tasks only.
                if (obj.str("parent") != null) return@mapNotNull null
                RemoteTask(
                    id = id,
                    title = obj.str("title").orEmpty().trim(),
                    notes = obj.str("notes").orEmpty().take(500),
                    due = obj.str("due")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    completed = obj.str("status") == "completed",
                    link = obj.str("webViewLink")
                )
            }

        /** The task object in an insert/patch reply, however deeply Composio wrapped it. */
        fun findTask(element: JsonElement, depth: Int = 0): JsonObject? {
            if (depth > 8) return null
            return when (val node = ComposioJson.unwrap(element)) {
                is JsonObject -> if (node.str("id") != null && (node.containsKey("title") || node.str("kind") == "tasks#task")) node
                else node.values.firstNotNullOfOrNull { findTask(it, depth + 1) }
                is JsonArray -> node.firstNotNullOfOrNull { findTask(it, depth + 1) }
                else -> null
            }
        }

        private fun findString(element: JsonElement, key: String, depth: Int = 0): String? {
            if (depth > 8) return null
            return when (val node = ComposioJson.unwrap(element)) {
                is JsonObject -> ComposioJson.text(node[key]) ?: node.values.firstNotNullOfOrNull { findString(it, key, depth + 1) }
                is JsonArray -> node.firstNotNullOfOrNull { findString(it, key, depth + 1) }
                else -> null
            }
        }
    }
}
