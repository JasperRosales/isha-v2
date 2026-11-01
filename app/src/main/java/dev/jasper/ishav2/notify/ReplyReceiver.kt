package dev.jasper.ishav2.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dev.jasper.ishav2.IshaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the inline "Reply" action on isha's notifications: the typed text is
 * sent into the same conversation, and the answer comes back as a new push.
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val container = (context.applicationContext as IshaApp).container
        if (text.isBlank()) {
            container.notifier.cancelReply(conversationId)
            return
        }

        val pending = goAsync()
        scope.launch {
            try {
                val title = container.conversations.list()
                    .firstOrNull { it.id == conversationId }
                    ?.title
                    .orEmpty()
                container.notifier.postReplyPending(conversationId, title, text)
                val accepted = container.chat.replyFrom(conversationId, text)
                if (!accepted) {
                    container.notifier.postReply(
                        conversationId,
                        title,
                        text,
                        "I'm still finishing something else. Open the chat and send that again in a moment."
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "dev.jasper.ishav2.action.REPLY"
        const val KEY_TEXT = "reply_text"
        const val EXTRA_CONVERSATION_ID = "conversation_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
