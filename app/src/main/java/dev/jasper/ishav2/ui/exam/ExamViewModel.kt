package dev.jasper.ishav2.ui.exam

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.Exam
import dev.jasper.ishav2.data.ExamAttempt
import dev.jasper.ishav2.data.ExamConfig
import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ExamSourceFile
import dev.jasper.ishav2.data.ItemType
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.exam.ExamSources
import dev.jasper.ishav2.exam.GenerationProgress
import dev.jasper.ishav2.exam.Grading
import dev.jasper.ishav2.util.FileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the new-exam form holds before generating. */
data class ExamDraft(
    val title: String = "",
    val files: List<ExamSourceFile> = emptyList(),
    val pasted: String = "",
    val config: ExamConfig = ExamConfig(),
    val importing: Boolean = false
) {
    val hasSource: Boolean get() = files.isNotEmpty() || pasted.isNotBlank()
    val canGenerate: Boolean get() = hasSource && config.total > 0 && !importing
}

/** One run through an exam. */
data class ExamSession(
    val examId: String,
    val title: String,
    val items: List<ExamItem>,
    val instantFeedback: Boolean,
    val missedOnly: Boolean,
    val index: Int = 0,
    val responses: Map<String, String> = emptyMap(),
    /** Items whose answer is locked and shown (instant feedback). */
    val revealed: Set<String> = emptySet(),
    val startedAt: Long = System.currentTimeMillis()
) {
    val current: ExamItem get() = items[index]
    val answered: Int get() = items.count { !responses[it.id].isNullOrBlank() }
    val isLast: Boolean get() = index == items.lastIndex
}

data class ExamResult(
    val examId: String,
    val attemptId: String,
    val title: String,
    val items: List<ExamItem>,
    val responses: Map<String, String>,
    val correct: Set<String>,
    val seconds: Int,
    val missedOnly: Boolean
) {
    val score: Int get() = correct.size
    val percent: Int get() = if (items.isEmpty()) 0 else Math.round(score * 100f / items.size)
    val missed: List<ExamItem> get() = items.filterNot { it.id in correct }
}

sealed interface ExamStage {
    /** Depth, for the direction of screen transitions. */
    val depth: Int

    data object Library : ExamStage { override val depth = 0 }
    data object Create : ExamStage { override val depth = 1 }
    data class Generating(
        val title: String,
        val progress: GenerationProgress,
        /** Adding items to this exam instead of making a new one. */
        val appendTo: String? = null
    ) : ExamStage { override val depth = 2 }
    data class Detail(val examId: String) : ExamStage { override val depth = 1 }
    data class Taking(val session: ExamSession) : ExamStage { override val depth = 2 }
    data class Results(val result: ExamResult) : ExamStage { override val depth = 3 }
}

class ExamViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.exams
    val exams = store.exams

    private val _stage = MutableStateFlow<ExamStage>(ExamStage.Library)
    val stage = _stage.asStateFlow()

    private val _draft = MutableStateFlow(ExamDraft())
    val draft = _draft.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var generation: Job? = null

    // ---- Navigation ---------------------------------------------------------

    /** Returns false when the screen itself should close. */
    fun back(): Boolean {
        when (val s = _stage.value) {
            ExamStage.Library -> return false
            ExamStage.Create -> _stage.value = ExamStage.Library
            is ExamStage.Detail -> _stage.value = ExamStage.Library
            is ExamStage.Generating -> cancelGeneration()
            is ExamStage.Taking -> _stage.value = ExamStage.Detail(s.session.examId)
            is ExamStage.Results -> _stage.value = ExamStage.Detail(s.result.examId)
        }
        return true
    }

    fun newExam() {
        // Files imported into a draft that was never turned into an exam.
        val abandoned = _draft.value.files
        if (abandoned.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) { abandoned.forEach { File(it.path).delete() } }
        }
        _draft.value = ExamDraft()
        _stage.value = ExamStage.Create
    }

    fun open(exam: Exam) {
        _stage.value = ExamStage.Detail(exam.id)
    }

    // ---- New exam form -----------------------------------------------------

    fun editDraft(transform: (ExamDraft) -> ExamDraft) = _draft.update(transform)

    fun setCount(type: ItemType, count: Int) = _draft.update { d ->
        val others = d.config.counts.filterKeys { it != type }.values.sum()
        val value = count.coerceIn(0, minOf(ExamConfig.MAX_PER_TYPE, ExamConfig.MAX_TOTAL - others))
        d.copy(config = d.config.copy(counts = d.config.counts + (type to value)))
    }

    fun applyPreset(mc: Int, tf: Int, id: Int) = _draft.update { d ->
        d.copy(config = d.config.copy(counts = mapOf(
            ItemType.MULTIPLE_CHOICE to mc, ItemType.TRUE_FALSE to tf, ItemType.IDENTIFICATION to id
        )))
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _draft.update { it.copy(importing = true) }
            for (uri in uris) {
                runCatching { importOne(uri) }
                    .onSuccess { file ->
                        if (ExamSources.kindOf(file) == ExamSources.Kind.UNSUPPORTED) {
                            File(file.path).delete()
                            _events.tryEmit("${file.name}: use Markdown, text, PDF, Word, PowerPoint or photos.")
                        } else {
                            _draft.update { d ->
                                d.copy(
                                    files = d.files + file,
                                    title = d.title.ifBlank { file.name.substringBeforeLast('.').replace('_', ' ').take(60) }
                                )
                            }
                        }
                    }
                    .onFailure { _events.tryEmit(it.message ?: "Couldn't open that file.") }
            }
            _draft.update { it.copy(importing = false) }
        }
    }

    fun removeFile(file: ExamSourceFile) {
        _draft.update { d -> d.copy(files = d.files - file) }
        viewModelScope.launch(Dispatchers.IO) { File(file.path).delete() }
    }

    private suspend fun importOne(uri: Uri): ExamSourceFile = withContext(Dispatchers.IO) {
        val part = FileStore.importUri(container.appContext, uri)
        val (path, mime, name) = when (part) {
            is Part.Image -> Triple(part.path, part.mime, part.name)
            is Part.File -> Triple(part.path, part.mime, part.name)
            else -> error("Unsupported file")
        }
        // Keep it with the exams, so clearing chats doesn't remove an exam's source.
        val from = File(path)
        val to = File(store.sourcesDir, from.name)
        val moved = from.renameTo(to) || runCatching { from.copyTo(to, overwrite = true); from.delete() }.isSuccess
        ExamSourceFile(if (moved) to.absolutePath else from.absolutePath, mime, name)
    }

    fun generate() {
        val d = _draft.value
        if (!d.canGenerate) return
        val title = d.title.trim().ifBlank { "Practice exam" }
        runGeneration(title, appendTo = null) {
            val prepared = withContext(Dispatchers.IO) { ExamSources.prepare(d.files, d.pasted) }
            val pastedFile = if (d.pasted.isNotBlank()) savePasted(d.pasted) else null
            val result = container.examGenerator.generate(prepared, d.config) { p -> progress(title, p, null) }
            val exam = Exam(
                title = title,
                config = d.config,
                items = result.items,
                sources = d.files + listOfNotNull(pastedFile)
            )
            store.save(exam)
            _draft.value = ExamDraft()
            result.notes.forEach { _events.tryEmit(it) }
            ExamStage.Detail(exam.id)
        }
    }

    /** Writes more items for an existing exam from its saved sources. */
    fun addItems(exam: Exam, counts: Map<ItemType, Int>) {
        if (counts.values.sum() <= 0) return
        runGeneration(exam.title, appendTo = exam.id) {
            val prepared = withContext(Dispatchers.IO) { ExamSources.prepare(exam.sources, "") }
            val result = container.examGenerator.generate(
                prepared, exam.config, counts, avoid = exam.items.map { it.question }
            ) { p -> progress(exam.title, p, exam.id) }
            store.edit(exam.id) { it.copy(items = it.items + result.items) }
            _events.tryEmit("Added ${result.items.size} item${if (result.items.size == 1) "" else "s"}.")
            result.notes.forEach { _events.tryEmit(it) }
            ExamStage.Detail(exam.id)
        }
    }

    private fun progress(title: String, p: GenerationProgress, appendTo: String?) {
        if (_stage.value is ExamStage.Generating) _stage.value = ExamStage.Generating(title, p, appendTo)
    }

    private fun runGeneration(title: String, appendTo: String?, work: suspend () -> ExamStage) {
        generation?.cancel()
        val returnTo = _stage.value
        _stage.value = ExamStage.Generating(title, GenerationProgress(0, 0), appendTo)
        generation = viewModelScope.launch {
            try {
                _stage.value = work()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "Couldn't write the exam.")
                _stage.value = returnTo
            }
        }
    }

    fun cancelGeneration() {
        val s = _stage.value as? ExamStage.Generating
        generation?.cancel()
        generation = null
        _stage.value = if (s?.appendTo != null) ExamStage.Detail(s.appendTo) else ExamStage.Create
    }

    private fun savePasted(text: String): ExamSourceFile {
        val file = File(store.sourcesDir, "pasted-${System.currentTimeMillis()}.md")
        file.writeText(text)
        return ExamSourceFile(file.absolutePath, "text/markdown", "Pasted notes")
    }

    // ---- Exam detail -------------------------------------------------------

    fun setInstantFeedback(exam: Exam, on: Boolean) = viewModelScope.launch {
        store.edit(exam.id) { it.copy(config = it.config.copy(instantFeedback = on)) }
    }

    fun setShuffle(exam: Exam, on: Boolean) = viewModelScope.launch {
        store.edit(exam.id) { it.copy(config = it.config.copy(shuffle = on)) }
    }

    fun rename(exam: Exam, title: String) = viewModelScope.launch {
        val clean = title.trim().take(80)
        if (clean.isNotEmpty()) store.edit(exam.id) { it.copy(title = clean) }
    }

    fun deleteItem(exam: Exam, item: ExamItem) = viewModelScope.launch {
        store.edit(exam.id) { it.copy(items = it.items.filterNot { i -> i.id == item.id }) }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            store.delete(exam.id)
            _events.tryEmit("Deleted “${exam.title}”.")
        }
        _stage.value = ExamStage.Library
    }

    // ---- Taking an exam ----------------------------------------------------

    fun start(exam: Exam, missedOnly: Boolean = false) {
        val pool = if (missedOnly) exam.lastMissed else exam.items
        if (pool.isEmpty()) return
        val items = if (exam.config.shuffle) pool.shuffled().map { it.copy(choices = it.choices.shuffled()) } else pool
        _stage.value = ExamStage.Taking(
            ExamSession(
                examId = exam.id,
                title = exam.title,
                items = items,
                instantFeedback = exam.config.instantFeedback,
                missedOnly = missedOnly
            )
        )
    }

    private fun editSession(transform: (ExamSession) -> ExamSession) {
        val s = _stage.value as? ExamStage.Taking ?: return
        _stage.value = ExamStage.Taking(transform(s.session))
    }

    /** Typing or choosing. In instant-feedback mode a tapped choice locks in right away. */
    fun respond(item: ExamItem, response: String) = editSession { s ->
        if (item.id in s.revealed) return@editSession s
        val next = s.copy(responses = s.responses + (item.id to response))
        if (s.instantFeedback && item.type != ItemType.IDENTIFICATION) next.copy(revealed = next.revealed + item.id) else next
    }

    /** "Check" for a typed answer in instant-feedback mode. */
    fun check(item: ExamItem) = editSession { s ->
        if (s.responses[item.id].isNullOrBlank()) s else s.copy(revealed = s.revealed + item.id)
    }

    fun goTo(index: Int) = editSession { s -> s.copy(index = index.coerceIn(0, s.items.lastIndex)) }

    fun finish() {
        val s = (_stage.value as? ExamStage.Taking)?.session ?: return
        val correct = s.items.filter { Grading.isCorrect(it, s.responses[it.id]) }.map { it.id }.toSet()
        val seconds = ((System.currentTimeMillis() - s.startedAt) / 1000).toInt()
        val attempt = ExamAttempt(
            score = correct.size,
            total = s.items.size,
            missed = s.items.map { it.id }.filterNot { it in correct },
            missedOnly = s.missedOnly,
            seconds = seconds
        )
        viewModelScope.launch { store.edit(s.examId) { it.copy(attempts = it.attempts + attempt) } }
        _stage.value = ExamStage.Results(
            ExamResult(s.examId, attempt.id, s.title, s.items, s.responses, correct, seconds, s.missedOnly)
        )
    }

    /** "I was right": counts a typed answer the forgiving check still missed. */
    fun countAsCorrect(item: ExamItem) {
        val r = (_stage.value as? ExamStage.Results)?.result ?: return
        if (item.id in r.correct) return
        val updated = r.copy(correct = r.correct + item.id)
        _stage.value = ExamStage.Results(updated)
        viewModelScope.launch {
            store.edit(r.examId) { exam ->
                exam.copy(
                    attempts = exam.attempts.map {
                        if (it.id == r.attemptId) it.copy(score = updated.score, missed = it.missed - item.id) else it
                    },
                    // Remember this answer for next time.
                    items = exam.items.map { i ->
                        val given = r.responses[item.id]?.trim()
                        if (i.id == item.id && !given.isNullOrBlank()) i.copy(accepted = (i.accepted + given).distinct()) else i
                    }
                )
            }
        }
    }

    fun retake(result: ExamResult, missedOnly: Boolean) {
        val exam = store.get(result.examId) ?: return
        start(exam, missedOnly)
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ExamViewModel(container) }
        }
    }
}
