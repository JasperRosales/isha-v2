package dev.jasper.ishav2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Local inbox: reminders and messages isha sends you. */
class NotificationStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<InboxItem>>(emptyList())
    val items: StateFlow<List<InboxItem>> = _items.asStateFlow()

    init {
        _items.value = read()
    }

    suspend fun add(item: InboxItem): InboxItem = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = (listOf(item) + _items.value).take(MAX_ITEMS)
            write(next)
            _items.value = next
            item
        }
    }

    suspend fun add(
        title: String,
        message: String,
        level: String = "info",
        source: String = "isha"
    ): InboxItem = add(InboxItem(title = title, message = message, level = level, source = source))

    suspend fun markRead(id: String) = update { item ->
        if (item.id == id) item.copy(read = true) else item
    }

    suspend fun markAllRead() = update { it.copy(read = true) }

    suspend fun delete(id: String) = update { item ->
        if (item.id == id) null else item
    }

    suspend fun clearRead() = update { item ->
        if (item.read) null else item
    }

    suspend fun clear() = update { null }

    private suspend fun update(transform: (InboxItem) -> InboxItem?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = _items.value.mapNotNull(transform)
            write(next)
            _items.value = next
        }
    }

    private fun read(): List<InboxItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<InboxItem>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<InboxItem>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(items))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(items))
            temp.delete()
        }
    }

    private companion object {
        const val MAX_ITEMS = 200
    }
}
