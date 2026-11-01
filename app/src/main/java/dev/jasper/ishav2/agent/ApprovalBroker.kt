package dev.jasper.ishav2.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class ApprovalRequest(
    val callId: String,
    val toolName: String,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Bridges a tool call waiting for permission and the Approve/Deny card in the UI.
 * Only one action waits at a time because the agent executes tool calls in order.
 */
class ApprovalBroker {
    private val _pending = MutableStateFlow<ApprovalRequest?>(null)
    val pending: StateFlow<ApprovalRequest?> = _pending.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun request(callId: String, toolName: String, summary: String): Boolean {
        val waiter = CompletableDeferred<Boolean>()
        mutex.withLock {
            deferred?.complete(false)
            deferred = waiter
            _pending.value = ApprovalRequest(callId, toolName, summary)
        }
        val allowed = withTimeoutOrNull(TIMEOUT_MS) { waiter.await() } ?: false
        mutex.withLock {
            if (deferred === waiter) {
                deferred = null
                _pending.value = null
            }
        }
        return allowed
    }

    fun resolve(allow: Boolean) {
        val waiter = deferred ?: return
        _pending.value = null
        waiter.complete(allow)
    }

    fun cancelAll() {
        val waiter = deferred
        deferred = null
        _pending.value = null
        waiter?.complete(false)
    }

    private companion object {
        const val TIMEOUT_MS = 10 * 60 * 1000L
    }
}
