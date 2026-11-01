package dev.jasper.ishav2.agent

import android.util.Log
import dev.jasper.ishav2.data.Conversation
import dev.jasper.ishav2.data.ConversationStore
import dev.jasper.ishav2.data.ConversationSummary
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.data.SettingsStore
import dev.jasper.ishav2.data.conversationTitle
import dev.jasper.ishav2.data.snapshot
import dev.jasper.ishav2.data.userMessage
import dev.jasper.ishav2.net.ComposioManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the live conversation and the agent run for the whole app instead of
 * for one screen. The run survives navigation and ViewModel recreation, so a
 * reply keeps streaming (and keeps getting persisted) no matter which screen
 * is showing — the UI just re-attaches to these flows when it comes back.
 */
class ChatSession(
    private val settings: SettingsStore,
    private val store: ConversationStore,
    private val composio: ComposioManager,
    private val runner: AgentRunner,
    private val approvals: ApprovalBroker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _conversations = MutableStateFlow(store.list())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var runJob: Job? = null

    @Volatile
    private var opened = false

    /** Loads the last conversation once; safe to call again when a screen re-attaches. */
    suspend fun openInitial() {
        if (opened) return
        opened = true
        val lastId = settings.current.lastConversationId
        val loaded = lastId?.let { store.load(it) }
        // If the user already tapped send while this was loading, keep their chat.
        if (_conversation.value == null) {
            if (loaded != null) {
                _conversation.value = loaded.snapshot()
            } else {
                startFresh()
            }
        }
        maybeConnectComposio()
    }

    fun newChat() {
        scope.launch {
            stopAndWait()
            startFresh()
        }
    }

    fun openChat(id: String) {
        scope.launch {
            // Already showing it (possibly mid-reply): reloading would cancel the run.
            if (_conversation.value?.id == id) return@launch
            stopAndWait()
            val loaded = store.load(id) ?: return@launch
            _conversation.value = loaded.snapshot()
            settings.setLastConversation(id)
        }
    }

    fun send(text: String, attachments: List<Part>): Boolean {
        if (_busy.value) return false
        if (text.isBlank() && attachments.isEmpty()) return false
        if (!settings.current.canChat) {
            _events.tryEmit("Add a Gemini API key in Settings, or download an offline model.")
            return false
        }
        val current = _conversation.value ?: Conversation().also {
            _conversation.value = it.snapshot()
        }

        // Add the user's turn immediately and publish it, so tapping send
        // always shows the bubble even if the network is slow.
        val message = userMessage(text, attachments)
        current.messages += message
        if (current.title == "New chat" || current.title.isBlank()) {
            current.title = conversationTitle(text, attachments)
        }
        _conversation.value = current.snapshot()
        _busy.value = true
        Log.d(TAG, "send: conversation=${current.id} chars=${text.length}")

        runJob = scope.launch {
            var lastPersistAt = 0L
            val listener = object : AgentRunner.Listener {
                override fun onUpdate() {
                    // Called on the runner's own thread between mutations, so
                    // this deep copy is race-free.
                    val snapshot = current.snapshot()
                    _conversation.value = snapshot
                    // Also write through to disk while tokens arrive, so a
                    // process killed mid-stream still keeps the partial reply.
                    val now = System.currentTimeMillis()
                    if (now - lastPersistAt > PERSIST_DURING_STREAM_MS) {
                        lastPersistAt = now
                        scope.launch { persist(snapshot) }
                    }
                }

                override suspend fun onPersist() {
                    store.save(current)
                    _conversations.value = store.list()
                    _conversation.value = current.snapshot()
                }
            }
            try {
                runner.run(current, listener)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "send failed", error)
                _events.tryEmit(error.message ?: "Something went wrong.")
            } finally {
                withContext(NonCancellable) {
                    runCatching { store.save(current) }
                    _conversations.value = store.list()
                    _conversation.value = current.snapshot()
                    _busy.value = false
                }
            }
        }
        return true
    }

    /**
     * Inline reply from a notification: switch to that conversation (the app
     * may not even be open) and send. Returns false if a reply is already running.
     */
    suspend fun replyFrom(conversationId: String, text: String): Boolean {
        if (_busy.value) return false
        if (_conversation.value?.id != conversationId) {
            val loaded = store.load(conversationId) ?: return false
            _conversation.value = loaded.snapshot()
            settings.setLastConversation(conversationId)
            opened = true
        }
        return send(text, emptyList())
    }

    /**
     * Runs a prompt in a brand-new conversation without touching the one on
     * screen: used by the daily briefing. Approval-gated actions are declined
     * because nobody is there to approve them.
     */
    suspend fun runInBackground(title: String, prompt: String): Conversation {
        val conversation = Conversation(title = title)
        conversation.messages += userMessage(prompt, emptyList())
        val listener = object : AgentRunner.Listener {
            override fun onUpdate() = Unit

            override suspend fun onPersist() {
                store.save(conversation)
                _conversations.value = store.list()
            }
        }
        try {
            runner.run(conversation, listener, interactive = false)
        } finally {
            withContext(NonCancellable) {
                runCatching { store.save(conversation) }
                _conversations.value = store.list()
            }
        }
        return conversation
    }

    fun stop() {
        approvals.cancelAll()
        runJob?.cancel()
        runJob = null
    }

    fun renameConversation(id: String, title: String) {
        val clean = title.trim().take(80)
        if (clean.isBlank()) return
        scope.launch {
            store.rename(id, clean)
            _conversations.value = store.list()
            val current = _conversation.value
            if (current?.id == id) {
                _conversation.value = current.copy(title = clean)
            }
        }
    }

    fun deleteConversation(id: String) {
        scope.launch {
            val isCurrent = _conversation.value?.id == id
            if (isCurrent) stopAndWait()
            store.delete(id)
            _conversations.value = store.list()
            if (isCurrent) startFresh()
        }
    }

    fun report(message: String) {
        _events.tryEmit(message)
    }

    /**
     * "Clear chats"/"clear everything" ran from Settings: stop any live run,
     * drop the in-memory conversation, and make sure a cancelled run's final
     * save cannot resurrect the wiped chat on disk.
     */
    fun onStoreCleared() {
        scope.launch {
            val wipedId = _conversation.value?.id
            stopAndWait()
            if (wipedId != null) store.delete(wipedId)
            _conversations.value = store.list()
            startFresh()
        }
    }

    private suspend fun persist(snapshot: Conversation) {
        runCatching { store.save(snapshot) }
        _conversations.value = store.list()
    }

    private suspend fun maybeConnectComposio() {
        if (settings.current.hasComposioKey && !composio.status.value.connected) {
            runCatching { composio.ensure(force = false) }
        }
    }

    private suspend fun stopAndWait() {
        approvals.cancelAll()
        val job = runJob
        runJob = null
        job?.cancelAndJoin()
    }

    private suspend fun startFresh() {
        val fresh = Conversation()
        settings.setLastConversation(fresh.id)
        _conversation.value = fresh.snapshot()
    }

    private companion object {
        private const val TAG = "isha-chat"
        private const val PERSIST_DURING_STREAM_MS = 2_000L
    }
}
