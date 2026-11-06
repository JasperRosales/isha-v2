package dev.jasper.ishav2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.data.CloudBrain
import dev.jasper.ishav2.data.ExtraProvider
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

    private val _testingGroq = MutableStateFlow(false)
    val testingGroq = _testingGroq.asStateFlow()

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

    fun saveGroqKey(value: String) {
        container.settings.saveGroqKey(value)
        _events.tryEmit(
            when {
                value.isNotBlank() -> "Groq key saved."
                container.settings.current.hasGroqKey -> "Using the built-in Groq key."
                else -> "Groq key removed · the next brain will answer."
            }
        )
    }

    fun setGroqModel(value: String) {
        container.settings.setGroqModel(value)
        _events.tryEmit("Groq model set to ${container.settings.current.groqModel}")
    }

    /** Cooldowns per cloud brain, shown in the priority list. */
    val limitedUntil = container.runner.limitedUntil

    private val _testingExtra = MutableStateFlow(false)
    val testingExtra = _testingExtra.asStateFlow()

    /** Models the extra brain's key can use, filled by its Test button. */
    private val _extraModels = MutableStateFlow<List<String>>(emptyList())
    val extraModels = _extraModels.asStateFlow()

    /** Moves [brain] one step up (-1) or down (+1) in the priority list. */
    fun moveBrain(brain: CloudBrain, delta: Int) {
        val order = container.settings.current.brainOrder.toMutableList()
        val from = order.indexOf(brain)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.removeAt(from)
        order.add(to, brain)
        container.settings.setBrainOrder(order)
        val s = container.settings.current
        _events.tryEmit("Order: " + s.brainOrder.joinToString(" → ") { s.brainLabel(it) })
    }

    fun retryBrain(brain: CloudBrain) {
        container.runner.clearLimit(brain)
        _events.tryEmit("${container.settings.current.brainLabel(brain)} will be tried again.")
    }

    fun setSlowSwitch(seconds: Int) {
        container.settings.setSlowSwitchSeconds(seconds)
        _events.tryEmit(if (seconds == 0) "Slow-reply switch off." else "Switch brains after ${seconds}s with no reply.")
    }

    fun saveExtraKey(value: String) {
        container.settings.saveExtraKey(value)
        container.runner.clearLimit(CloudBrain.EXTRA)
        _events.tryEmit(if (value.isBlank()) "Extra brain key removed." else "Extra brain key saved.")
    }

    fun setExtraProvider(provider: ExtraProvider) {
        container.settings.setExtraProvider(provider)
        _extraModels.value = emptyList()
        container.runner.clearLimit(CloudBrain.EXTRA)
    }

    fun setExtraModel(value: String) {
        container.settings.setExtraModel(value)
        container.runner.clearLimit(CloudBrain.EXTRA)
        _events.tryEmit("Extra brain model set to ${value.trim()}")
    }

    fun setExtraBaseUrl(value: String) {
        container.settings.setExtraBaseUrl(value)
        container.runner.clearLimit(CloudBrain.EXTRA)
        _events.tryEmit("Base URL saved.")
    }

    fun testExtra() {
        if (_testingExtra.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _testingExtra.value = true
            try {
                val models = container.extra.listModels()
                _extraModels.value = models
                val current = container.extra.model
                _events.emit(
                    if (models.contains(current)) "${container.extra.name} works · ${models.size} models"
                    else "${container.extra.name} works, but \"$current\" isn't listed. Pick a model below."
                )
            } catch (error: Exception) {
                _events.emit(error.message ?: "Test failed.")
            } finally {
                _testingExtra.value = false
            }
        }
    }

    fun testGroq() {
        if (_testingGroq.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _testingGroq.value = true
            try {
                val models = container.groq.listModels()
                val current = container.groq.model
                _events.emit(
                    if (models.contains(current)) "Groq works · ${models.size} models available"
                    else "Groq works, but \"$current\" is not in the list. Pick another model."
                )
            } catch (error: Exception) {
                _events.emit(error.message ?: "Groq test failed.")
            } finally {
                _testingGroq.value = false
            }
        }
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

    fun setTasksSync(enabled: Boolean) {
        container.settings.setTasksSync(enabled)
        if (enabled) {
            viewModelScope.launch {
                val error = container.sync.syncTasks()
                _events.tryEmit(error ?: "Due is now synced with Google Tasks.")
            }
        } else {
            _events.tryEmit("Google Tasks sync turned off.")
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

    fun setVoiceReadReplies(enabled: Boolean) {
        container.settings.setVoice(readReplies = enabled)
    }

    fun setVoiceHandsFree(enabled: Boolean) {
        container.settings.setVoice(handsFree = enabled)
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
        _events.tryEmit("Preparing your briefing. It will land in your inbox.")
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
