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
enum class ItemType(val label: String, val short: String) {
    MULTIPLE_CHOICE("Multiple choice", "MC"),
    TRUE_FALSE("True or false", "T/F"),
    IDENTIFICATION("Identification", "ID")
}

@Serializable
enum class Difficulty(val label: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), MIXED("Mixed") }

/** One exam question. For true/false the answer is "True" or "False"; for multiple choice it is one of [choices]. */
@Serializable
data class ExamItem(
    val id: String = UUID.randomUUID().toString().take(8),
    val type: ItemType,
    val question: String,
    val choices: List<String> = emptyList(),
    val answer: String,
    /** Other answers that count for identification items (synonyms, abbreviations). */
    val accepted: List<String> = emptyList(),
    val explanation: String = "",
    val topic: String = ""
)

/** How the exam is made and taken. Item counts are per type. */
@Serializable
data class ExamConfig(
    val counts: Map<ItemType, Int> = mapOf(
        ItemType.MULTIPLE_CHOICE to 10,
        ItemType.TRUE_FALSE to 5,
        ItemType.IDENTIFICATION to 5
    ),
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** "" = same language as the material. */
    val language: String = "",
    /** Optional topics to focus on, in the user's words. */
    val focus: String = "",
    /** Show right/wrong and the explanation after every item, instead of only at the end. */
    val instantFeedback: Boolean = true,
    val shuffle: Boolean = true
) {
    val total: Int get() = counts.values.sum()

    companion object {
        const val MAX_PER_TYPE = 40
        const val MAX_TOTAL = 60
    }
}

/** A finished attempt. [missed] are the ids answered wrong or skipped. */
@Serializable
data class ExamAttempt(
    val id: String = UUID.randomUUID().toString().take(8),
    val at: Long = System.currentTimeMillis(),
    val score: Int,
    val total: Int,
    val missed: List<String> = emptyList(),
    /** True when only the previously missed items were asked. */
    val missedOnly: Boolean = false,
    val seconds: Int = 0
) {
    val percent: Int get() = if (total == 0) 0 else Math.round(score * 100f / total)
}

/** A source file saved with the exam, so more items can be written from it later. */
@Serializable
data class ExamSourceFile(val path: String, val mime: String, val name: String)

@Serializable
data class Exam(
    val id: String = UUID.randomUUID().toString().take(10),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val config: ExamConfig = ExamConfig(),
    val items: List<ExamItem> = emptyList(),
    val attempts: List<ExamAttempt> = emptyList(),
    val sources: List<ExamSourceFile> = emptyList()
) {
    val best: ExamAttempt? get() = attempts.filterNot { it.missedOnly }.maxByOrNull { it.percent }
    val last: ExamAttempt? get() = attempts.maxByOrNull { it.at }

    /** Items missed in the latest attempt that still exist. */
    val lastMissed: List<ExamItem>
        get() {
            val missed = last?.missed?.toSet().orEmpty()
            return items.filter { it.id in missed }
        }

    fun countOf(type: ItemType): Int = items.count { it.type == type }
}

@Serializable
private data class ExamFile(val exams: List<Exam> = emptyList())

/** Exams kept on the phone as one JSON file; sources live next to it in [sourcesDir]. */
class ExamStore(private val file: File) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _exams = MutableStateFlow(read())
    val exams: StateFlow<List<Exam>> = _exams.asStateFlow()

    val sourcesDir: File get() = File(file.parentFile, "exam_sources").apply { mkdirs() }

    fun get(id: String): Exam? = _exams.value.firstOrNull { it.id == id }

    suspend fun save(exam: Exam) = update { list ->
        if (list.any { it.id == exam.id }) list.map { if (it.id == exam.id) exam else it } else listOf(exam) + list
    }

    suspend fun delete(id: String) {
        val exam = get(id)
        update { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) {
            exam?.sources?.forEach { runCatching { File(it.path).delete() } }
        }
    }

    suspend fun edit(id: String, transform: (Exam) -> Exam) = update { list ->
        list.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun update(transform: (List<Exam>) -> List<Exam>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(_exams.value)
            write(next)
            _exams.value = next
        }
    }

    private fun read(): List<Exam> {
        if (!file.exists()) return emptyList()
        return runCatching { json.decodeFromString<ExamFile>(file.readText()).exams }.getOrDefault(emptyList())
    }

    private fun write(exams: List<Exam>) {
        file.parentFile?.mkdirs()
        val text = json.encodeToString(ExamFile(exams))
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            file.writeText(text)
            temp.delete()
        }
    }
}
