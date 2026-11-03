package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.MailItem
import dev.jasper.ishav2.net.ComposioJson.obj
import dev.jasper.ishav2.net.ComposioJson.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Fetches recent inbox mail (headers + preview only) through Composio. */
class GmailSync(private val calls: ComposioCalls) {

    suspend fun fetch(query: String = QUERY, max: Int = 25): List<MailItem> = parseMessages(
        calls.run(
            FETCH_EMAILS,
            buildJsonObject {
                put("query", query)
                put("max_results", max)
                put("verbose", false)
                put("include_payload", false)
                put("user_id", "me")
            }
        )
    ).sortedByDescending { it.receivedAt }

    companion object {
        val FETCH_EMAILS = listOf("GMAIL_FETCH_EMAILS")

        /** Last two days of the main inbox; promos and social are noise for a digest. */
        const val QUERY = "in:inbox newer_than:2d -category:promotions -category:social"

        /** New-mail alerts: unread primary inbox mail from the last day. */
        const val UNREAD_QUERY = "is:unread in:inbox newer_than:1d -category:promotions -category:social"

        fun parseMessages(element: JsonElement): List<MailItem> =
            ComposioJson.findArray(element, setOf("messages", "emails")).orEmpty().mapNotNull { node ->
                val obj = ComposioJson.unwrap(node) as? JsonObject ?: return@mapNotNull null
                val id = obj.str("messageId", "message_id", "id") ?: return@mapNotNull null
                val labels = (obj["labelIds"] as? JsonArray ?: obj["label_ids"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .orEmpty()
                val preview = obj.obj("preview")
                MailItem(
                    id = id,
                    threadId = obj.str("threadId", "thread_id"),
                    from = cleanSender(obj.str("sender", "from").orEmpty()),
                    subject = obj.str("subject") ?: preview?.str("subject") ?: "(no subject)",
                    snippet = (preview?.str("body") ?: obj.str("snippet", "messageText", "preview"))
                        .orEmpty()
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(300),
                    receivedAt = parseTime(obj.str("messageTimestamp", "internalDate", "date", "timestamp")),
                    unread = "UNREAD" in labels,
                    important = "IMPORTANT" in labels || "STARRED" in labels
                )
            }

        /** "Jane Cruz <jane@x.com>" → "Jane Cruz". */
        fun cleanSender(raw: String): String {
            val name = raw.substringBefore("<").trim().trim('"')
            return name.ifBlank { raw.trim().trim('<', '>') }
        }

        fun parseTime(raw: String?): Long {
            if (raw.isNullOrBlank()) return 0
            raw.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
            runCatching { return OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            runCatching { return ZonedDateTime.parse(raw).toInstant().toEpochMilli() }
            runCatching { return ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            return 0
        }
    }
}
