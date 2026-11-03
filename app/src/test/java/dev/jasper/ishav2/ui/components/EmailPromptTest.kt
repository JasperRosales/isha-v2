package dev.jasper.ishav2.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPromptTest {
    @Test
    fun writeForMeIncludesToneAndPoints() {
        val prompt = buildEmailPrompt(
            EmailDraft(
                to = "santos@school.edu",
                message = "can't attend Friday\nask for makeup quiz",
                tone = EmailTone.FORMAL
            )
        )
        assertTrue(prompt.startsWith("Write an email for me in Gmail:"))
        assertTrue(prompt.contains("- To: santos@school.edu"))
        assertTrue(prompt.contains("- Subject: (suggest a clear subject)"))
        assertTrue(prompt.contains("- Tone: formal"))
        assertTrue(prompt.contains("  can't attend Friday\n  ask for makeup quiz"))
        assertFalse(prompt.contains("Cc:"))
        assertFalse(prompt.contains("find their address"))
        assertTrue(prompt.endsWith("wait for me to say send before sending it."))
    }

    @Test
    fun exactWordsWithNameRecipientAndCc() {
        val prompt = buildEmailPrompt(
            EmailDraft(to = "Prof Santos", message = "Hi po!", subject = "Absence", cc = "mia@x.com", writeForMe = false)
        )
        assertTrue(prompt.contains("using my exact words"))
        assertTrue(prompt.contains("- Cc: mia@x.com"))
        assertTrue(prompt.contains("- Subject: Absence"))
        assertFalse(prompt.contains("Tone:"))
        assertTrue(prompt.contains("find their address in my recent Gmail"))
    }
}
