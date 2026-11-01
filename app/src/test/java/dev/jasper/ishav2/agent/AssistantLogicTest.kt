package dev.jasper.ishav2.agent

import dev.jasper.ishav2.data.ChatMessage
import dev.jasper.ishav2.data.Conversation
import dev.jasper.ishav2.data.MemoryFact
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.data.Role
import dev.jasper.ishav2.local.InstalledModel
import dev.jasper.ishav2.net.ComposioManager
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssistantLogicTest {

    @Test
    fun readOnlySlugsSkipApproval() {
        assertTrue(ComposioManager.isReadOnlySlug("GMAIL_FETCH_EMAILS"))
        assertTrue(ComposioManager.isReadOnlySlug("GOOGLECALENDAR_LIST_EVENTS"))
        assertTrue(ComposioManager.isReadOnlySlug("GOOGLEDRIVE_FIND_FILE"))
        assertFalse(ComposioManager.isReadOnlySlug("GMAIL_SEND_EMAIL"))
        assertFalse(ComposioManager.isReadOnlySlug("GOOGLECALENDAR_CREATE_EVENT"))
        assertFalse(ComposioManager.isReadOnlySlug("GMAIL_REPLY_TO_THREAD"))
        // "list" plus a write verb is still a write.
        assertFalse(ComposioManager.isReadOnlySlug("TODOIST_DELETE_LIST"))
        // No recognisable verb: treat as a write and ask.
        assertFalse(ComposioManager.isReadOnlySlug("NOTION_PAGE"))
    }

    @Test
    fun offlinePromptDropsOldTurnsToFit() {
        val conversation = Conversation()
        repeat(30) { i ->
            conversation.messages += ChatMessage(role = Role.USER, parts = mutableListOf(Part.Text("question $i " + "x".repeat(300))))
            conversation.messages += ChatMessage(role = Role.MODEL, parts = mutableListOf(Part.Text("answer $i " + "y".repeat(300))))
        }
        conversation.messages += ChatMessage(role = Role.USER, parts = mutableListOf(Part.Text("What should I do first?")))
        val model = InstalledModel("m", "Test", File("m.task"), maxTokens = 1280)
        val prompt = buildOfflinePrompt(
            conversation = conversation,
            model = model,
            memory = listOf(MemoryFact(text = "Studies at BatStateU")),
            countTokens = { it.length / 4 }
        )
        assertTrue(prompt.endsWith("What should I do first?"))
        assertTrue(prompt.contains("Studies at BatStateU"))
        assertFalse("oldest turns are dropped", prompt.contains("question 0 "))
        assertTrue(prompt.length / 4 <= 1280 - 384)
    }

    @Test
    fun offlineReplyStripsRoleEchoes() {
        assertEquals("Sure! Here's a plan.", cleanOfflineReply("isha: Sure! Here's a plan.\nUser: thanks"))
        assertEquals("Hello", cleanOfflineReply("  Hello  "))
    }

    @Test
    fun oldConversationsWithoutBrainStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"id":"a","role":"MODEL","parts":[{"type":"text","text":"hi"}],"status":"DONE"}"""
        val message = json.decodeFromString<ChatMessage>(legacy)
        assertNull(message.brain)
        assertEquals("hi", (message.parts.single() as Part.Text).text)
    }
}
