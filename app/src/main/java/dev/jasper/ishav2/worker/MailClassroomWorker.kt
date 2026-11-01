package dev.jasper.ishav2.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.data.InboxItem
import dev.jasper.ishav2.util.Notifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class MailClassroomWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? IshaApp ?: return Result.success()
        val container = app.container
        val settings = container.settings
        val current = settings.current

        if (!current.hasComposioKey) return Result.success()
        if (current.composioMode == dev.jasper.ishav2.data.ComposioKeyMode.CONNECT) {
            // Connect MCP doesn't expose accounts() easily; best-effort skip
        }

        var hadWork = false

        if (current.gmailPollingEnabled && current.composioMode == dev.jasper.ishav2.data.ComposioKeyMode.PROJECT) {
            hadWork = checkGmail(container) || hadWork
        }
        if (current.classroomPollingEnabled && current.composioMode == dev.jasper.ishav2.data.ComposioKeyMode.PROJECT) {
            hadWork = checkClassroom(container) || hadWork
        }

        return if (hadWork) Result.success() else Result.success()
    }

    private suspend fun checkGmail(container: dev.jasper.ishav2.agent.AppContainer): Boolean {
        val settings = container.settings
        val current = settings.current
        val composio = container.composio
        return try {
            composio.ensure(force = false)
            val since = if (current.gmailLastCheckedAt == 0L) System.currentTimeMillis() - 86_400_000L else current.gmailLastCheckedAt
            val q = "is:unread after:${(since / 1000L)}"
            val res = composio.execute(
                "GMAIL_LIST_MESSAGES",
                buildJsonObject {
                    put("q", q)
                    put("maxResults", 10)
                }
            )
            val parsed = Json.parseToJsonElement(res)
            val items = when (parsed) {
                is JsonArray -> parsed
                is JsonObject -> parsed["messages"]?.jsonArray
                    ?: parsed["items"]?.jsonArray
                    ?: parsed["data"]?.jsonArray
                    ?: parsed["result"]?.let { r ->
                        when (r) {
                            is JsonArray -> r
                            is JsonObject -> r["messages"]?.jsonArray ?: r["items"]?.jsonArray
                            else -> null
                        }
                    }
                else -> null
            }
            if (items.isNullOrEmpty()) {
                settings.setGmailLastChecked(System.currentTimeMillis(), current.gmailLastMessageId)
                return false
            }
            val firstId = items.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
            if (firstId != null && firstId == current.gmailLastMessageId && current.gmailLastCheckedAt != 0L) {
                settings.setGmailLastChecked(System.currentTimeMillis(), current.gmailLastMessageId)
                return false
            }
            for (msg in items.take(5)) {
                val obj = msg.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                val subject = obj["subject"]?.jsonPrimitive?.content ?: obj["snippet"]?.jsonPrimitive?.content ?: "New Gmail message"
                val from = obj["from"]?.jsonPrimitive?.content ?: obj["sender"]?.jsonPrimitive?.content ?: ""
                container.notifications.add(
                    InboxItem(
                        title = "Gmail: $subject",
                        message = if (from.isNotBlank()) "From $from" else "New unread message",
                        level = "info",
                        source = "gmail"
                    )
                )
                Notifier.postLocal(container.appContext, "Gmail: $subject", if (from.isNotBlank()) "From $from" else "New unread message")
                if (firstId != null && id == firstId) break
            }
            settings.setGmailLastChecked(System.currentTimeMillis(), firstId ?: current.gmailLastMessageId)
            true
        } catch (e: Exception) {
            settings.setGmailLastChecked(System.currentTimeMillis(), current.gmailLastMessageId)
            false
        }
    }

    private suspend fun checkClassroom(container: dev.jasper.ishav2.agent.AppContainer): Boolean {
        val settings = container.settings
        val current = settings.current
        val composio = container.composio
        return try {
            composio.ensure(force = false)
            val res = composio.execute(
                "GOOGLECLASSROOM_LIST_COURSES",
                buildJsonObject { put("pageSize", 5) }
            )
            val parsed = Json.parseToJsonElement(res)
            val courses = when (parsed) {
                is JsonArray -> parsed
                is JsonObject -> parsed["courses"]?.jsonArray
                    ?: parsed["items"]?.jsonArray
                    ?: parsed["data"]?.jsonArray
                    ?: parsed["result"]?.let { r ->
                        when (r) {
                            is JsonArray -> r
                            is JsonObject -> r["courses"]?.jsonArray ?: r["items"]?.jsonArray
                            else -> null
                        }
                    }
                else -> null
            }
            if (courses.isNullOrEmpty()) {
                settings.setClassroomLastChecked(System.currentTimeMillis(), current.classroomLastActivityId)
                return false
            }
            var foundNew = false
            for (c in courses.take(3)) {
                val cobj = c.jsonObject
                val cid = cobj["id"]?.jsonPrimitive?.content ?: continue
                val cname = cobj["name"]?.jsonPrimitive?.content ?: "Classroom"
                val ann = runCatching {
                    composio.execute(
                        "GOOGLECLASSROOM_LIST_ANNOUNCEMENTS",
                        buildJsonObject {
                            put("courseId", cid)
                            put("pageSize", 5)
                        }
                    )
                }.getOrNull() ?: continue
                val p2 = Json.parseToJsonElement(ann)
                val anns = when (p2) {
                    is JsonArray -> p2
                    is JsonObject -> p2["announcements"]?.jsonArray
                        ?: p2["items"]?.jsonArray
                        ?: p2["data"]?.jsonArray
                        ?: p2["result"]?.let { r ->
                            when (r) {
                                is JsonArray -> r
                                is JsonObject -> r["announcements"]?.jsonArray ?: r["items"]?.jsonArray
                                else -> null
                            }
                        }
                    else -> null
                }
                if (!anns.isNullOrEmpty()) {
                    val a = anns.first().jsonObject
                    val aid = a["id"]?.jsonPrimitive?.content ?: cid + ":" + System.currentTimeMillis()
                    if (aid != current.classroomLastActivityId || current.classroomLastCheckedAt == 0L) {
                        val text = a["text"]?.jsonPrimitive?.content ?: "New announcement"
                        container.notifications.add(
                            InboxItem(
                                title = "Classroom: $cname",
                                message = text.take(200),
                                level = "info",
                                source = "classroom"
                            )
                        )
                        Notifier.postLocal(container.appContext, "Classroom: $cname", text.take(120))
                        settings.setClassroomLastChecked(System.currentTimeMillis(), aid)
                        foundNew = true
                        break
                    }
                }
            }
            if (!foundNew) settings.setClassroomLastChecked(System.currentTimeMillis(), current.classroomLastActivityId)
            foundNew
        } catch (e: Exception) {
            settings.setClassroomLastChecked(System.currentTimeMillis(), current.classroomLastActivityId)
            false
        }
    }

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrEmpty(): JsonArray? = null
}
