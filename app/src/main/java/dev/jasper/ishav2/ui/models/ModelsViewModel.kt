package dev.jasper.ishav2.ui.models

import android.app.ActivityManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.BrainMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    private val models = container.models

    val settings = container.settings.settings
    val installed = models.installed
    val downloads = models.downloads
    val online = container.network.online

    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    /** Total RAM in whole GB, to warn before downloading a model the phone can't run. */
    val deviceRamGb: Int = run {
        val info = ActivityManager.MemoryInfo()
        container.appContext.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        ((info.totalMem + (512L shl 20)) shr 30).toInt()
    }

    init {
        models.refresh()
    }

    fun setBrainMode(mode: BrainMode) {
        container.settings.setBrainMode(mode)
    }

    fun download(id: String) = models.download(id)

    fun cancel(id: String) = models.cancel(id)

    fun delete(id: String) {
        if (container.models.active()?.id == id) container.localLlm.unload()
        models.delete(id)
        _events.tryEmit("Model deleted.")
    }

    fun select(id: String) {
        models.select(id)
        _events.tryEmit("Offline brain set.")
        // Warm the weights up now so the first offline reply is quick.
        val model = models.installed.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching { container.localLlm.ensureLoaded(model) }
                .onFailure { _events.emit("Could not load ${model.name}: ${it.message}") }
        }
    }

    fun saveToken(token: String) {
        container.settings.saveHuggingFaceToken(token)
        _events.tryEmit(if (token.isBlank()) "Token removed." else "Hugging Face token saved.")
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _importing.value = true
            try {
                val model = models.import(uri)
                if (container.settings.current.localModelId == null) models.select(model.id)
                _events.emit("Imported ${model.name}.")
            } catch (error: Exception) {
                _events.emit(error.message ?: "Import failed.")
            } finally {
                _importing.value = false
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ModelsViewModel(container) }
        }
    }
}
