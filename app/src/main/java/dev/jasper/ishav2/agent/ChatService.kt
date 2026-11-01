package dev.jasper.ishav2.agent

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.data.Conversation
import dev.jasper.ishav2.data.MessageStatus
import dev.jasper.ishav2.data.Role
import dev.jasper.ishav2.data.plainText
import dev.jasper.ishav2.util.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps an in-flight agent run alive while the app is in the background: an
 * ongoing low-priority notification while the assistant is working, and a
 * "reply ready" push (only when the app is not on screen) once it finishes.
 *
 * [ChatSession] still owns the run itself; this service only pins the process
 * so Android cannot freeze or kill it mid-stream, and deep-links the user back
 * into the chat when the reply lands.
 */
class ChatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var chat: ChatSession
    private lateinit var notifier: Notifier

    @Volatile
    private var working = false

    override fun onCreate() {
        super.onCreate()
        val container = (application as IshaApp).container
        chat = container.chat
        notifier = container.notifier
        scope.launch {
            chat.busy.collect { busy ->
                if (busy) {
                    promote()
                } else if (working) {
                    finishRun()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must happen within the system's grace period, before
        // the busy flow gets a chance to emit, or the app gets killed for it.
        promote()
        // The run may have finished before this start was delivered; settle
        // immediately instead of leaving a foreground service with no work.
        if (!chat.busy.value) finishRun()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun promote() {
        working = true
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, Notifier.WORK_NOTIF_ID, notifier.workNotification(), type)
        }
    }

    private fun finishRun() {
        working = false
        notifyIfBackgrounded(chat.conversation.value)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyIfBackgrounded(conversation: Conversation?) {
        if (conversation == null || Notifier.isAppVisible()) return
        val last = conversation.messages.lastOrNull { it.role == Role.MODEL } ?: return
        val reply = when (last.status) {
            MessageStatus.DONE -> last.plainText().trim()
            MessageStatus.ERROR -> "I hit a problem: ${last.error ?: "something went wrong"}"
            else -> ""
        }
        if (reply.isBlank()) return
        val userText = conversation.messages
            .lastOrNull { it.role == Role.USER }
            ?.plainText()
        notifier.postReply(conversation.id, conversation.title, userText, reply)
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ChatService::class.java)
                )
            }
        }
    }
}
