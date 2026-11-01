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
import java.util.UUID

@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val message: String,
    val at: Long
)

/** Upcoming reminders, kept on disk so they can be re-armed after a reboot. */
class ReminderStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<Reminder>> = _items.asStateFlow()

    suspend fun add(reminder: Reminder) = update { it.filterNot { r -> r.id == reminder.id } + reminder }

    suspend fun remove(id: String): Reminder? {
        val found = _items.value.firstOrNull { it.id == id }
        update { list -> list.filterNot { it.id == id } }
        return found
    }

    fun upcoming(): List<Reminder> = _items.value.sortedBy { it.at }

    private suspend fun update(transform: (List<Reminder>) -> List<Reminder>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_items.value).sortedBy { it.at }
            write(next)
            _items.value = next
        }
    }

    private fun read(): List<Reminder> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<Reminder>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun write(items: List<Reminder>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(items))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(items))
            temp.delete()
        }
    }
}
