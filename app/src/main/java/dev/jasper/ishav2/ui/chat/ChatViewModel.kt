package dev.jasper.ishav2.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jasper.ishav2.agent.AppContainer
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.data.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin screen-facing adapter over the app-scoped [dev.jasper.ishav2.agent.ChatSession].
 * The conversation, the busy flag and the agent run itself are owned by the
 * session (and therefore survive navigation and ViewModel recreation); this
 * class only exposes them to the chat UI and wires the screen's lifecycle to
 * the session's initial load.
 */
class ChatViewModel(private val container: AppContainer) : ViewModel() {

    private val session = container.chat

    val settings = container.settings.settings
    val composioStatus = container.composio.status
    val pendingApproval = container.approvals.pending

    val conversations = session.conversations
    val conversation = session.conversation
    val busy = session.busy
    val events = session.events

    /** What will answer the next message, e.g. Gemini or the on-device model. */
    val brain = combine(
        container.settings.settings,
        container.network.online,
        container.models.installed
    ) { _, _, _ -> container.runner.plannedBrain() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), container.runner.plannedBrain())

    val inboxUnread = container.notifications.items
        .map { items -> items.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch(Dispatchers.IO) { session.openInitial() }
    }

    fun newChat() = session.newChat()

    fun openChat(id: String) = session.openChat(id)

    fun send(text: String, attachments: List<Part>): Boolean = session.send(text, attachments)

    fun stop() = session.stop()

    fun resolveApproval(allow: Boolean) {
        container.approvals.resolve(allow)
    }

    fun renameConversation(id: String, title: String) = session.renameConversation(id, title)

    fun deleteConversation(id: String) = session.deleteConversation(id)

    fun setApprovalMode(mode: ApprovalMode) {
        container.settings.setApprovalMode(mode)
    }

    fun refreshComposio() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.composio.ensure(force = false) }
        }
    }

    fun report(message: String) = session.report(message)

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { ChatViewModel(container) }
        }
    }
}
