package dev.jasper.ishav2.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskPromptTest {
    @Test
    fun fullDraftIncludesEveryField() {
        val prompt = buildTaskPrompt(
            TaskDraft(title = " Submit lab report ", notes = "Chem 101", due = LocalDate.of(2026, 11, 5), list = "School")
        )
        assertTrue(prompt.startsWith("Add this task to my Google Tasks:"))
        assertTrue(prompt.contains("- Title: Submit lab report\n"))
        assertTrue(prompt.contains("- Notes: Chem 101"))
        assertTrue(prompt.contains("- Due: 2026-11-05 (Thursday, Nov 5)"))
        assertTrue(prompt.contains("- List: School"))
    }

    @Test
    fun optionalFieldsAreOmitted() {
        val prompt = buildTaskPrompt(TaskDraft(title = "Buy milk"))
        assertFalse(prompt.contains("Notes:"))
        assertFalse(prompt.contains("Due:"))
        assertEquals(1, prompt.lines().count { it.startsWith("- List: my default list") })
    }
}
