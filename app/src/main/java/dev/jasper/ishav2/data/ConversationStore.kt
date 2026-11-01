package dev.jasper.ishav2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Conversation history stored as plain JSON, one file per chat. */
class ConversationStore(root: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val dir = File(root, "conversations").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val mutex = Mutex()

    @Volatile
    private var index: List<ConversationSummary> = loadIndex()

    fun list(): List<ConversationSummary> = index.sortedByDescending { it.updatedAt }

    fun summaries(): List<ConversationSummary> = list()

    suspend fun load(id: String): Conversation? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) return@withContext null
        val conversation = runCatching {
            json.decodeFromString<Conversation>(file.readText())
        }.getOrNull()
        conversation?.settleInterrupted()
        conversation
    }

    suspend fun save(conversation: Conversation) = withContext(Dispatchers.IO) {
        mutex.withLock {
            conversation.updatedAt = System.currentTimeMillis()
            writeAtomic(File(dir, "${conversation.id}.json"), json.encodeToString(conversation))
            val summary = ConversationSummary(
                id = conversation.id,
                title = conversation.title,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
                messageCount = conversation.messages.size
            )
            val next = index.filterNot { it.id == conversation.id } + summary
            index = next
            writeIndex(next)
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(dir, "$id.json")
            if (file.exists()) {
                runCatching {
                    val conversation = json.decodeFromString<Conversation>(file.readText())
                    conversation.title = title
                    writeAtomic(file, json.encodeToString(conversation))
                }
            }
            val next = index.map { if (it.id == id) it.copy(title = title) else it }
            index = next
            writeIndex(next)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(dir, "$id.json").delete()
            val next = index.filterNot { it.id == id }
            index = next
            writeIndex(next)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
            index = emptyList()
            writeIndex(emptyList())
        }
    }

    private fun readIndex(): List<ConversationSummary> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ConversationSummary>>(indexFile.readText())
        }.getOrDefault(emptyList())
    }

    /**
     * The index can be missing or stale (older builds, a crash mid-write, or a
     * file copied in). Rebuild it from the conversation files so saved chats
     * always appear in the drawer.
     */
    private fun loadIndex(): List<ConversationSummary> {
        val byId = readIndex().associateBy { it.id }.toMutableMap()
        dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.forEach { file ->
                val conversation = runCatching {
                    json.decodeFromString<Conversation>(file.readText())
                }.getOrNull() ?: return@forEach
                byId[conversation.id] = ConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    createdAt = conversation.createdAt,
                    updatedAt = conversation.updatedAt,
                    messageCount = conversation.messages.size
                )
            }
        return byId.values.toList()
    }

    private fun writeIndex(next: List<ConversationSummary>) {
        writeAtomic(indexFile, json.encodeToString(next))
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }
}

/**
 * A run that was interrupted (process killed mid-stream) leaves messages stuck
 * in STREAMING. Settle them so a reloaded chat shows the partial reply instead
 * of a "thinking" bubble that never resolves.
 */
private fun Conversation.settleInterrupted() {
    for (message in messages) {
        if (message.status != MessageStatus.STREAMING) continue
        val hasText = message.parts
            .filterIsInstance<Part.Text>()
            .any { !it.thought && it.text.isNotBlank() }
        val hasCalls = message.parts.any { it is Part.ToolCall }
        if (hasText || hasCalls) {
            message.status = MessageStatus.DONE
        } else {
            message.status = MessageStatus.ERROR
            message.error = "Interrupted before the reply arrived. Send the message again."
        }
    }
}
