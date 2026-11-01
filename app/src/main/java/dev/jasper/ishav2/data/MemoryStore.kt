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
data class MemoryFact(
    val id: String = UUID.randomUUID().toString().take(8),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Small, user-visible long-term memory: preferences, names, routines the user
 * asked isha to remember. Injected into every system prompt, editable in Settings.
 */
class MemoryStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _facts = MutableStateFlow(read())
    val facts: StateFlow<List<MemoryFact>> = _facts.asStateFlow()

    suspend fun remember(text: String): MemoryFact = withContext(Dispatchers.IO) {
        mutex.withLock {
            val clean = text.trim().take(MAX_FACT_CHARS)
            val existing = _facts.value.firstOrNull { it.text.equals(clean, ignoreCase = true) }
            if (existing != null) return@withLock existing
            val fact = MemoryFact(text = clean)
            val next = (_facts.value + fact).takeLast(MAX_FACTS)
            write(next)
            _facts.value = next
            fact
        }
    }

    /** Removes facts by id, or whose text contains [query]. Returns how many were removed. */
    suspend fun forget(query: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val needle = query.trim()
            if (needle.isBlank()) return@withLock 0
            val next = _facts.value.filterNot {
                it.id == needle || it.text.contains(needle, ignoreCase = true)
            }
            val removed = _facts.value.size - next.size
            if (removed > 0) {
                write(next)
                _facts.value = next
            }
            removed
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            write(emptyList())
            _facts.value = emptyList()
        }
    }

    private fun read(): List<MemoryFact> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<MemoryFact>>(file.readText())
        }.getOrDefault(emptyList())
    }

    private fun write(facts: List<MemoryFact>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(facts))
        if (!temp.renameTo(file)) {
            file.writeText(json.encodeToString(facts))
            temp.delete()
        }
    }

    private companion object {
        const val MAX_FACTS = 60
        const val MAX_FACT_CHARS = 300
    }
}
