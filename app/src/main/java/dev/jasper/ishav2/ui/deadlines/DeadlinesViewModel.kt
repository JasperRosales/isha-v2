package dev.jasper.ishav2.ui.deadlines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.Deadline
import dev.jasper.ishav2.ui.components.TaskDraft
import dev.jasper.ishav2.ui.components.buildTaskPrompt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class DeadlinesViewModel(private val container: AppContainer) : ViewModel() {

    val data = container.deadlines.data
    val syncing = container.sync.syncingDeadlines
    val online = container.network.online

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        // Fresh enough? Then don't spend a sync just for opening the screen.
        val last = data.value.lastSyncedAt
        if (container.settings.current.hasComposioKey && container.network.isOnline() &&
            System.currentTimeMillis() - last > AUTO_SYNC_AFTER_MS
        ) {
            sync(quiet = true)
        }
    }

    fun sync(quiet: Boolean = false) {
        viewModelScope.launch {
            val error = container.sync.syncDeadlines()
            if (error != null && !quiet) _events.tryEmit(error)
            if (error == null && !quiet) _events.tryEmit("Deadlines are up to date.")
        }
    }

    fun setDone(item: Deadline, done: Boolean) {
        viewModelScope.launch { container.deadlines.setDone(item.id, done) }
    }

    fun delete(item: Deadline) {
        viewModelScope.launch {
            container.deadlines.delete(item.id)
            _events.tryEmit(
                when {
                    item.fromClassroom -> "Removed. It won't come back on the next sync."
                    item.taskId != null && container.settings.current.tasksSyncEnabled -> "Deleted here and in Google Tasks."
                    else -> "Deleted."
                }
            )
        }
    }

    fun add(title: String, course: String, notes: String, dueAt: Long, hasTime: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            container.deadlines.add(
                Deadline(title = title.trim(), course = course.trim(), notes = notes.trim(), dueAt = dueAt, hasTime = hasTime)
            )
            _events.tryEmit("Saved. isha will remind you before it's due.")
        }
    }

    /** Sends a Google Tasks request for [item] to the chat. @return false if the chat is busy. */
    fun sendToTasks(item: Deadline): Boolean {
        val due = Instant.ofEpochMilli(item.dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val notes = listOfNotNull(
            item.course.takeIf { it.isNotBlank() },
            item.link
        ).joinToString(" · ")
        val sent = container.chat.send(
            buildTaskPrompt(TaskDraft(title = item.title, notes = notes, due = due)),
            emptyList()
        )
        if (!sent) _events.tryEmit("isha is busy with another reply. Try again in a moment.")
        return sent
    }

    companion object {
        private const val AUTO_SYNC_AFTER_MS = 30L * 60 * 1000

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { DeadlinesViewModel(container) }
        }
    }
}
