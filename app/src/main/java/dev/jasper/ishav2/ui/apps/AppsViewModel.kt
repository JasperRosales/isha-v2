package dev.jasper.ishav2.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.net.ConnectedAccount
import dev.jasper.ishav2.net.ToolkitInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppsViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings.settings
    val status = container.composio.status

    private val _accounts = MutableStateFlow<List<ConnectedAccount>>(emptyList())
    val accounts = _accounts.asStateFlow()

    private val _catalog = MutableStateFlow<List<ToolkitInfo>>(emptyList())
    val catalog = _catalog.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _link = MutableStateFlow<String?>(null)
    val link = _link.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val events = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            runCatching { container.composio.ensure(force = false) }
            loadAccounts()
            _loading.value = false
        }
    }

    private var searchJob: Job? = null

    fun search(value: String) {
        _query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            _catalog.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(200)
            _loading.value = true
            runCatching { container.composio.catalog(value.trim()) }
                .onSuccess { _catalog.value = it }
                .onFailure { _events.emit(it.message ?: "Could not search apps.") }
            _loading.value = false
        }
    }

    fun connect(slug: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            runCatching { container.composio.startLink(slug) }
                .onSuccess { url ->
                    if (url.isNullOrBlank()) {
                        _events.emit("Composio did not return a login link. Open the dashboard to connect ${slug.replace('_', ' ')}.")
                    } else {
                        _link.value = url
                    }
                }
                .onFailure { _events.emit(it.message ?: "Could not start the connection.") }
            _loading.value = false
        }
    }

    fun consumeLink() {
        _link.value = null
    }

    fun openDashboardUrl(): String = container.composio.dashboardUrl()

    fun connectMode(): Boolean = settings.value.composioMode == ComposioKeyMode.CONNECT

    fun report(message: String) {
        _events.tryEmit(message)
    }

    private suspend fun loadAccounts() {
        if (container.settings.current.composioMode != ComposioKeyMode.PROJECT) {
            _accounts.value = emptyList()
            return
        }
        _accounts.value = runCatching { container.composio.accounts() }.getOrDefault(emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
    }

    companion object {
        fun factory(container: AppContainer) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return AppsViewModel(container) as T
            }
        }
    }
}
