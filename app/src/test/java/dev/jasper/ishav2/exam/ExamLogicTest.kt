package dev.jasper.ishav2.exam

import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamLogicTest {

    @Test
    fun parsesAllTypesAndDropsBadOnes() {
        val reply = """
            <think>planning</think>
            ```json
            {"items":[
              {"type":"multiple_choice","question":"What is the powerhouse of the cell?","choices":["A) Nucleus","B) Mitochondria","C) Ribosome","D) Golgi body"],"answer":"B","explanation":"It makes ATP.","topic":"Cells"},
              {"type":"true_false","question":"Plants make food by photosynthesis.","answer":true},
              {"type":"identification","question":"What molecule carries energy in cells?","answer":"ATP","accepted":["adenosine triphosphate","ATP"]},
              {"type":"multiple_choice","question":"Broken item with two choices","choices":["x","y"],"answer":"x"},
              {"type":"essay","question":"Unsupported type here"},
              {"type":"multiple_choice","question":"Answer not among the choices?","choices":["a1","b1","c1","d1"],"answer":"zzz"}
            ]}
            ```
        """.trimIndent()
        val items = ExamPrompt.parse(reply, Random(1))
        assertEquals(3, items.size)
        val mc = items[0]
        assertEquals(ItemType.MULTIPLE_CHOICE, mc.type)
        assertEquals("Mitochondria", mc.answer)
        assertTrue(mc.choices.containsAll(listOf("Nucleus", "Mitochondria", "Ribosome", "Golgi body")))
        assertEquals("True", items[1].answer)
        assertEquals(listOf("adenosine triphosphate"), items[2].accepted)
    }

    @Test
    fun parsesBareArrayAndFilipinoBooleans() {
        val items = ExamPrompt.parse("""[{"type":"tf","question":"Ang araw ay bituin.","answer":"Tama"}]""")
        assertEquals("True", items.single().answer)
    }

    @Test
    fun garbageGivesNothing() {
        assertTrue(ExamPrompt.parse("Sorry, I can't help").isEmpty())
    }

    @Test
    fun identificationIsForgiving() {
        val item = ExamItem(type = ItemType.IDENTIFICATION, question = "q", answer = "Photosynthesis", accepted = listOf("photo synthesis"))
        assertTrue(Grading.isCorrect(item, "photosynthesis"))
        assertTrue(Grading.isCorrect(item, "Photosynthesis."))
        assertTrue(Grading.isCorrect(item, "photosinthesis")) // one typo
        assertTrue(Grading.isCorrect(item, "the photosynthesis"))
        assertFalse(Grading.isCorrect(item, "respiration"))
        assertFalse(Grading.isCorrect(item, ""))
    }

    @Test
    fun numbersAndShortAnswersMustBeExact() {
        val year = ExamItem(type = ItemType.IDENTIFICATION, question = "q", answer = "1898")
        assertTrue(Grading.isCorrect(year, "1898"))
        assertFalse(Grading.isCorrect(year, "1899"))
        val dna = ExamItem(type = ItemType.IDENTIFICATION, question = "q", answer = "DNA")
        assertFalse(Grading.isCorrect(dna, "RNA"))
        assertTrue(Grading.isCorrect(ExamItem(type = ItemType.IDENTIFICATION, question = "q", answer = "José Rizal"), "jose rizal"))
    }

    @Test
    fun choiceItemsMatchExactly() {
        val tf = ExamItem(type = ItemType.TRUE_FALSE, question = "q", answer = "False")
        assertTrue(Grading.isCorrect(tf, "false"))
        assertFalse(Grading.isCorrect(tf, "True"))
        assertFalse(Grading.isCorrect(tf, null))
    }

    @Test
    fun readsOfficeXml() {
        val xml = """<w:document><w:body><w:p><w:pPr/><w:r><w:t>Cell</w:t></w:r><w:r><w:t xml:space="preserve"> theory &amp; parts</w:t></w:r></w:p><w:p/><w:p><w:r><w:t>Second line</w:t></w:r></w:p></w:body></w:document>"""
        assertEquals("Cell theory & parts\nSecond line", ExamSources.officeXmlText(xml, "w:p", "w:t"))
    }

    @Test
    fun readsHtml() {
        assertEquals("Title\n Body text", ExamSources.htmlText("<h1>Title</h1><script>x()</script><p>Body&nbsp;text</p>").trimEnd())
    }

    @Test
    fun promptMentionsCountsAndAvoids() {
        val system = ExamPrompt.system(dev.jasper.ishav2.data.ExamConfig(focus = "chapter 2"), listOf("Old question?"))
        assertTrue(system.contains("Focus on: chapter 2"))
        assertTrue(system.contains("Old question?"))
        val request = ExamPrompt.request(mapOf(ItemType.MULTIPLE_CHOICE to 4, ItemType.TRUE_FALSE to 0, ItemType.IDENTIFICATION to 2), "notes")
        assertTrue(request.contains("4 multiple_choice, 2 identification"))
        assertFalse(request.contains("true_false"))
    }
}
