package dev.jasper.ishav2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.worker.BriefingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
    val composio = container.composio.status
    val memory = container.memory.facts
    val installedModels = container.models.installed

    private val _testingGemini = MutableStateFlow(false)
    val testingGemini = _testingGemini.asStateFlow()

    private val _testingComposio = MutableStateFlow(false)
    val testingComposio = _testingComposio.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _cleared = MutableStateFlow(false)
    val cleared = _cleared.asStateFlow()

    fun saveGeminiKey(value: String) {
        container.settings.saveGeminiKey(value)
        _events.tryEmit(if (value.isBlank()) "Gemini key removed." else "Gemini key saved.")
    }

    fun saveComposioKey(value: String) {
        container.settings.saveComposioKey(value)
        container.composio.reset()
        if (value.isBlank()) {
            _events.tryEmit("Composio key removed.")
        } else {
            _events.tryEmit("Composio key saved. Connecting…")
            viewModelScope.launch(Dispatchers.IO) {
                val status = runCatching { container.composio.ensure(force = true) }.getOrNull()
                if (status?.connected == true) {
                    _events.emit("Composio connected · ${status.toolCount} tools")
                } else if (status?.error != null) {
                    _events.emit(status.error)
                }
            }
        }
    }

    fun setModel(value: String) {
        container.settings.setModel(value)
        _events.tryEmit("Model set to ${container.settings.current.model}")
    }

    fun setApproval(mode: ApprovalMode) {
        container.settings.setApprovalMode(mode)
    }

    fun setMaxSteps(steps: Int) {
        container.settings.setMaxSteps(steps)
    }

    fun setDarkTheme(enabled: Boolean) {
        container.settings.setDarkTheme(enabled)
    }

    fun setGmailPolling(enabled: Boolean) {
        container.settings.setGmailPolling(enabled)
        if (enabled) {
            _events.tryEmit("Gmail background checks every 30 min enabled (needs Composio Gmail connected).")
        } else {
            _events.tryEmit("Gmail background checks disabled.")
        }
    }

    fun setClassroomPolling(enabled: Boolean) {
        container.settings.setClassroomPolling(enabled)
        if (enabled) {
            _events.tryEmit("Google Classroom background checks every 30 min enabled.")
        } else {
            _events.tryEmit("Google Classroom background checks disabled.")
        }
    }

    fun setAutoApproveReads(enabled: Boolean) {
        container.settings.setAutoApproveReads(enabled)
    }

    fun setBriefing(enabled: Boolean, hour: Int, minute: Int) {
        container.settings.setBriefing(enabled, hour, minute)
        BriefingWorker.schedule(container.appContext, container.settings.current)
        if (enabled) {
            _events.tryEmit("Daily briefing set for %02d:%02d.".format(hour, minute))
        } else {
            _events.tryEmit("Daily briefing turned off.")
        }
    }

    fun sendBriefingNow() {
        BriefingWorker.runNow(container.appContext)
        _events.tryEmit("Preparing your briefing. It will arrive as a notification.")
    }

    fun forgetFact(id: String) {
        viewModelScope.launch { container.memory.forget(id) }
    }

    fun clearMemory() {
        viewModelScope.launch {
            container.memory.clear()
            _events.emit("isha's memory cleared.")
        }
    }

    fun testGemini() {
        if (_testingGemini.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _testingGemini.value = true
            try {
                val models = container.gemini.listModels()
                val current = container.settings.current.model
                val hasCurrent = models.contains(current)
                _events.emit(
                    if (hasCurrent) "Gemini works · ${models.size} models available"
                    else "Gemini works, but \"$current\" is not in the list. Pick another model."
                )
            } catch (error: Exception) {
                _events.emit(error.message ?: "Gemini test failed.")
            } finally {
                _testingGemini.value = false
            }
        }
    }

    fun testComposio() {
        if (_testingComposio.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _testingComposio.value = true
            try {
                val status = container.composio.ensure(force = true)
                _events.emit(
                    when {
                        status.connected -> "Composio connected · ${status.toolCount} tools"
                        status.error != null -> status.error
                        else -> "Composio is not connected."
                    }
                )
            } catch (error: Exception) {
                _events.emit(error.message ?: "Composio test failed.")
            } finally {
                _testingComposio.value = false
            }
        }
    }

    fun clearChats() {
        viewModelScope.launch(Dispatchers.IO) {
            container.conversations.clear()
            container.notifications.clear()
            container.settings.clearChatPreferences()
            container.chat.onStoreCleared()
            _events.emit("Chats and inbox cleared.")
        }
    }

    fun clearEverything() {
        viewModelScope.launch(Dispatchers.IO) {
            container.conversations.clear()
            container.notifications.clear()
            container.memory.clear()
            container.budget.clear()
            container.composio.reset()
            container.settings.clearEverything()
            container.chat.onStoreCleared()
            _events.emit("All local data and keys cleared.")
            _cleared.value = true
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
