package dev.jasper.ishav2.exam

import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ItemType
import java.text.Normalizer

object Grading {

    fun isCorrect(item: ExamItem, response: String?): Boolean {
        val given = response?.trim().orEmpty()
        if (given.isEmpty()) return false
        return when (item.type) {
            ItemType.MULTIPLE_CHOICE, ItemType.TRUE_FALSE -> given.equals(item.answer.trim(), ignoreCase = true)
            ItemType.IDENTIFICATION -> identificationMatches(given, listOf(item.answer) + item.accepted)
        }
    }

    /**
     * Forgiving match for typed answers: case, accents, punctuation, a leading
     * article and small typos don't count against the student.
     */
    fun identificationMatches(given: String, answers: List<String>): Boolean {
        val g = normalize(given)
        if (g.isEmpty()) return false
        return answers.map(::normalize).filter { it.isNotEmpty() }.any { a ->
            a == g || a.replace(" ", "") == g.replace(" ", "") || typoClose(a, g)
        }
    }

    fun normalize(text: String): String {
        val noAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return noAccents.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(ARTICLE, "")
            .trim()
    }

    private val ARTICLE = Regex("^(the|a|an|ang|ang mga|mga|si|ni) ")

    private fun typoClose(answer: String, given: String): Boolean {
        // Numbers and very short answers must be exact.
        if (answer.any { it.isDigit() } || answer.length < 5) return false
        val allowed = if (answer.length >= 10) 2 else 1
        if (kotlin.math.abs(answer.length - given.length) > allowed) return false
        return levenshtein(answer, given) <= allowed
    }

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
