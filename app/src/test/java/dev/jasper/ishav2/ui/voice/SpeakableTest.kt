package dev.jasper.ishav2.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakableTest {

    @Test
    fun stripsMarkdownSyntax() {
        val md = """
            ## Today
            - **Math quiz** at *9 AM*
            - Read `chapter 3`
            > Don't forget ~~nothing~~
        """.trimIndent()
        assertEquals(
            "Today\nMath quiz at 9 AM\nRead chapter 3\nDon't forget nothing",
            speakableText(md)
        )
    }

    @Test
    fun linksAndUrlsReadAsWords() {
        assertEquals("See the syllabus.", speakableText("See [the syllabus](https://x.edu/s.pdf)."))
        assertEquals("Go to a link to example.com now", speakableText("Go to https://www.example.com/a?b=c now"))
    }

    @Test
    fun codeBlocksAreNotReadAloud() {
        val out = speakableText("Try this:\n```kotlin\nval x = 1\n```\nDone.")
        assertFalse(out.contains("val x"))
        assertTrue(out.contains("The code is on screen."))
    }

    @Test
    fun tablesBecomeLists() {
        val md = "| Item | Cost |\n|---|---:|\n| Lunch | 150 |"
        assertEquals("Item, Cost\nLunch, 150", speakableText(md))
    }

    @Test
    fun keepsMultiplicationAndSnakeCase() {
        assertEquals("2 * 3 is 6, see my_file", speakableText("2 * 3 is 6, see my_file"))
    }

    @Test
    fun dropsEmoji() {
        assertEquals("Done!", speakableText("Done! ✅"))
    }

    @Test
    fun chunksRespectMaxLength() {
        val text = "One two three. Four five six. Seven eight nine ten eleven twelve."
        val chunks = speechChunks(text, 20)
        assertTrue(chunks.all { it.length <= 20 })
        assertEquals(text.split(" ").joinToString(" "), chunks.joinToString(" "))
    }

    @Test
    fun chunksKeepParagraphsSeparate() {
        assertEquals(listOf("Hi.", "Second."), speechChunks("Hi.\n\nSecond.", 100))
    }
}
