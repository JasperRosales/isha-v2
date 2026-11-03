package dev.jasper.ishav2.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MailDigestViewModel(private val container: AppContainer) : ViewModel() {

    val data = container.mailDigest.data
    val syncing = container.sync.syncingMail
    val online = container.network.online

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        val last = data.value.lastSyncedAt
        if (container.settings.current.hasComposioKey && container.network.isOnline() &&
            System.currentTimeMillis() - last > AUTO_SYNC_AFTER_MS
        ) {
            sync(quiet = true)
        }
    }

    fun sync(quiet: Boolean = false) {
        viewModelScope.launch {
            val error = container.sync.syncMail()
            if (!quiet) _events.tryEmit(error ?: "Mail digest is up to date.")
        }
    }

    companion object {
        private const val AUTO_SYNC_AFTER_MS = 30L * 60 * 1000

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MailDigestViewModel(container) }
        }
    }
}
