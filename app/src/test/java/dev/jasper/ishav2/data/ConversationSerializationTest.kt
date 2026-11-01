package dev.jasper.ishav2.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun roundTripsConversationWithParts() {
        val conversation = Conversation(title = "hello").apply {
            messages += ChatMessage(
                role = Role.USER,
                parts = mutableListOf(Part.Text("List the files"), Part.File("/tmp/a", "text/plain", "a", 3))
            )
            messages += ChatMessage(
                role = Role.MODEL,
                parts = mutableListOf(
                    Part.ToolCall(
                        id = "call_1",
                        name = "list_dir",
                        argsJson = """{"path":"~"}""",
                        thoughtSignature = "sig"
                    )
                )
            )
            messages += ChatMessage(
                role = Role.TOOL,
                parts = mutableListOf(Part.ToolResponse("call_1", "list_dir", """{"ok":true}""", true))
            )
        }

        val encoded = json.encodeToString(conversation)
        assertTrue(encoded.contains("\"type\":\"text\""))
        val decoded = json.decodeFromString<Conversation>(encoded)
        assertEquals(conversation.messages.size, decoded.messages.size)
    }
}
