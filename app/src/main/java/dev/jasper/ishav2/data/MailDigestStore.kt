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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class MailItem(
    val id: String,
    val threadId: String? = null,
    val from: String = "",
    val subject: String = "",
    val snippet: String = "",
    val receivedAt: Long = 0,
    val unread: Boolean = false,
    val important: Boolean = false
)

/** The last Gmail sync, saved so it can be read offline. */
@Serializable
data class MailDigest(
    val items: List<MailItem> = emptyList(),
    /** isha's summary of [items], in Markdown. */
    val summary: String = "",
    /** Ids the summary was written for; a new set means it's due a refresh. */
    val summaryFor: List<String> = emptyList(),
    val summarizedAt: Long = 0,
    val lastSyncedAt: Long = 0,
    val lastSyncError: String? = null
) {
    val summaryStale: Boolean get() = items.map { it.id }.toSet() != summaryFor.toSet()
}

class MailDigestStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _data = MutableStateFlow(read())
    val data: StateFlow<MailDigest> = _data.asStateFlow()

    val current: MailDigest get() = _data.value

    suspend fun saveItems(items: List<MailItem>, at: Long = System.currentTimeMillis()) = update {
        it.copy(items = items, lastSyncedAt = at, lastSyncError = null)
    }

    suspend fun saveSummary(summary: String, forIds: List<String>, at: Long = System.currentTimeMillis()) = update {
        it.copy(summary = summary, summaryFor = forIds, summarizedAt = at)
    }

    suspend fun syncFailed(message: String) = update { it.copy(lastSyncError = message) }

    private suspend fun update(transform: (MailDigest) -> MailDigest) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_data.value)
            write(next)
            _data.value = next
        }
    }

    private fun read(): MailDigest {
        if (!file.exists()) return MailDigest()
        return runCatching { json.decodeFromString<MailDigest>(file.readText()) }.getOrDefault(MailDigest())
    }

    private fun write(data: MailDigest) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(data))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(data))
            temp.delete()
        }
    }

    companion object {
        /** Compact text for prompts (summary request, offline brain). */
        fun listForPrompt(items: List<MailItem>, zone: ZoneId = ZoneId.systemDefault(), limit: Int = 25): String =
            items.take(limit).joinToString("\n") { mail ->
                val time = if (mail.receivedAt > 0) {
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(mail.receivedAt), zone)
                        .format(DateTimeFormatter.ofPattern("EEE HH:mm"))
                } else "?"
                val flags = listOfNotNull("unread".takeIf { mail.unread }, "important".takeIf { mail.important })
                    .joinToString(",").let { if (it.isBlank()) "" else " [$it]" }
                "- $time · ${mail.from.take(60)} · ${mail.subject.take(120)}$flags — ${mail.snippet.take(200)}"
            }

        fun snapshotForPrompt(digest: MailDigest, zone: ZoneId = ZoneId.systemDefault()): String {
            if (digest.items.isEmpty()) return "Saved Gmail digest: empty (never synced or no recent mail)."
            val synced = ZonedDateTime.ofInstant(Instant.ofEpochMilli(digest.lastSyncedAt), zone)
                .format(DateTimeFormatter.ofPattern("EEE, MMM d HH:mm"))
            return buildString {
                appendLine("Saved Gmail digest (last synced $synced; may be out of date):")
                if (digest.summary.isNotBlank()) appendLine(digest.summary.take(1500))
                appendLine("Emails:")
                append(listForPrompt(digest.items, zone, limit = 12))
            }
        }
    }
}
