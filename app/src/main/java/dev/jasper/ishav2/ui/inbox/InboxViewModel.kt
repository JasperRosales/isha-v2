package dev.jasper.ishav2.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InboxViewModel(private val container: AppContainer) : ViewModel() {

    val items = container.notifications.items

    val unread = items
        .map { list -> list.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markRead(id: String) {
        viewModelScope.launch(Dispatchers.IO) { container.notifications.markRead(id) }
    }

    fun markAllRead() {
        viewModelScope.launch(Dispatchers.IO) { container.notifications.markAllRead() }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) { container.notifications.delete(id) }
    }

    fun clearRead() {
        viewModelScope.launch(Dispatchers.IO) { container.notifications.clearRead() }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { InboxViewModel(container) }
        }
    }
}
