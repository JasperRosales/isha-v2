package dev.jasper.ishav2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New chat",
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
)

@Serializable
data class ConversationSummary(
    val id: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    var messageCount: Int = 0
)

@Serializable
enum class Role { USER, MODEL, TOOL }

@Serializable
enum class MessageStatus { STREAMING, DONE, ERROR, STOPPED }

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val parts: MutableList<Part> = mutableListOf(),
    var status: MessageStatus = MessageStatus.DONE,
    var error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Which brain wrote this reply, e.g. "Gemini" or "On-device · Qwen 2.5 · 1.5B". */
    var brain: String? = null
)

/** One piece of a chat message. Serializable so conversations can live as JSON. */
@Serializable
sealed interface Part {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val thought: Boolean = false) : Part

    @Serializable
    @SerialName("image")
    data class Image(val path: String, val mime: String, val name: String) : Part

    @Serializable
    @SerialName("file")
    data class File(val path: String, val mime: String, val name: String, val size: Long) : Part

    @Serializable
    @SerialName("toolCall")
    data class ToolCall(
        val id: String,
        val name: String,
        val argsJson: String,
        val thoughtSignature: String? = null,
        var status: ToolStatus = ToolStatus.PENDING,
        var resultJson: String? = null,
        var error: String? = null
    ) : Part

    @Serializable
    @SerialName("toolResponse")
    data class ToolResponse(
        val callId: String,
        val name: String,
        val responseJson: String,
        val ok: Boolean
    ) : Part
}

@Serializable
enum class ToolStatus { PENDING, WAITING_APPROVAL, RUNNING, OK, ERROR, DENIED }

@Serializable
data class InboxItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String,
    val level: String = "info",
    val source: String = "isha",
    val createdAt: Long = System.currentTimeMillis(),
    var read: Boolean = false
)

fun Conversation.snapshot(): Conversation = copy(
    messages = messages.mapTo(mutableListOf()) { message ->
        message.copy(parts = message.parts.mapTo(mutableListOf()) { it.snapshot() })
    }
)

private fun Part.snapshot(): Part = when (this) {
    is Part.Text -> copy()
    is Part.Image -> copy()
    is Part.File -> copy()
    is Part.ToolCall -> copy()
    is Part.ToolResponse -> copy()
}

fun ChatMessage.plainText(): String = parts
    .filterIsInstance<Part.Text>()
    .filterNot { it.thought }
    .joinToString("") { it.text }

/** Short title for a new conversation, derived from the first message. */
fun conversationTitle(text: String, attachments: List<Part>): String {
    val base = text.trim().replace(Regex("\\s+"), " ").ifBlank {
        when (val first = attachments.firstOrNull()) {
            is Part.Image -> first.name
            is Part.File -> first.name
            else -> "New chat"
        }
    }
    return base.take(48).ifBlank { "New chat" }
}

/** The message a user just sent, including any attachments. */
fun userMessage(text: String, attachments: List<Part>): ChatMessage = ChatMessage(
    role = Role.USER,
    parts = buildList {
        if (text.isNotBlank()) add(Part.Text(text.trim()))
        addAll(attachments)
    }.toMutableList()
)
