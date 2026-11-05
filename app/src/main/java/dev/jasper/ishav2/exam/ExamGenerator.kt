package dev.jasper.ishav2.exam

import android.util.Base64
import dev.jasper.ishav2.data.ExamConfig
import dev.jasper.ishav2.data.ExamItem
import dev.jasper.ishav2.data.ItemType
import dev.jasper.ishav2.data.SettingsStore
import dev.jasper.ishav2.net.ApiException
import dev.jasper.ishav2.net.GeminiClient
import dev.jasper.ishav2.net.GroqClient
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

data class GenerationProgress(val made: Int, val total: Int, val note: String? = null)

data class GenerationResult(val items: List<ExamItem>, val notes: List<String>)

/**
 * Writes exam items from study material, a batch at a time so progress shows and
 * free-tier limits aren't hit by one huge reply. Groq is used for text that fits
 * its free limits; Gemini for PDFs, photos and long material, and as the backup.
 */
class ExamGenerator(
    private val settings: SettingsStore,
    private val groq: GroqClient,
    private val gemini: GeminiClient
) {
    private enum class Brain { GROQ, GEMINI }

    suspend fun generate(
        source: PreparedSource,
        config: ExamConfig,
        counts: Map<ItemType, Int> = config.counts,
        avoid: List<String> = emptyList(),
        onProgress: (GenerationProgress) -> Unit
    ): GenerationResult {
        val wanted = counts.filterValues { it > 0 }
        val total = wanted.values.sum()
        require(total > 0) { "Choose at least one item." }
        if (source.isEmpty) throw ApiException(400, "Add notes or a file to make the exam from.")

        val s = settings.current
        val notes = mutableListOf<String>()
        if (source.skipped.isNotEmpty()) notes += "Skipped: ${source.skipped.joinToString("; ")}."
        val needsGemini = source.media.isNotEmpty()
        if (needsGemini && !s.hasGeminiKey) {
            throw ApiException(
                401,
                "PDFs and photos are read by Gemini. Add a Gemini key in Settings, or use Markdown, text, Word or PowerPoint files (these work with Groq)."
            )
        }
        if (!s.hasCloudKey) throw ApiException(401, "Add a Groq or Gemini API key in Settings to write exams.")

        var brain = when {
            needsGemini -> Brain.GEMINI
            s.hasGroqKey && (source.text.length <= GROQ_MAX_CHARS || !s.hasGeminiKey) -> Brain.GROQ
            else -> Brain.GEMINI
        }
        if (brain == Brain.GROQ && source.text.length > GROQ_MAX_CHARS) {
            notes += "Only the first ${GROQ_MAX_CHARS / 1000}k characters were used (add a Gemini key for long material)."
        }

        val made = mutableListOf<ExamItem>()
        val remaining = wanted.toMutableMap()
        val seen = avoid.map { Grading.normalize(it) }.toMutableSet()
        var emptyRounds = 0
        var lastError: Exception? = null
        var switches = 0
        onProgress(GenerationProgress(0, total))

        while (remaining.values.sum() > 0 && emptyRounds < MAX_EMPTY_ROUNDS) {
            val batch = nextBatch(remaining)
            val reply = try {
                ask(brain, source, config, batch, avoid + made.map { it.question })
            } catch (e: ApiException) {
                lastError = e
                val fallback = fallbackFor(brain, needsGemini)
                when {
                    // Hand the rest to the other brain rather than making the student wait.
                    fallback != null && switches < 2 && (e.code == 429 || e.code == 413 || e.code >= 500 || e.code == 400) -> {
                        brain = fallback
                        switches++
                        notes += "Switched to ${if (brain == Brain.GEMINI) "Gemini" else "Groq"} part-way through."
                        continue
                    }
                    e.code == 429 && (e.retryAfterMs ?: Long.MAX_VALUE) <= MAX_WAIT_MS -> {
                        waitWithCountdown(e.retryAfterMs!!, made.size, total, onProgress)
                        emptyRounds++
                        continue
                    }
                    made.isEmpty() -> throw e
                    else -> break
                }
            } catch (e: IOException) {
                if (made.isEmpty()) throw ApiException(0, "Couldn't reach the AI. Check your connection and try again.")
                lastError = e
                break
            }

            var added = 0
            for (item in ExamPrompt.parse(reply)) {
                val left = remaining[item.type] ?: 0
                if (left <= 0) continue
                val key = Grading.normalize(item.question)
                if (!seen.add(key)) continue
                made += item
                remaining[item.type] = left - 1
                added++
            }
            if (added == 0) emptyRounds++ else emptyRounds = 0
            onProgress(GenerationProgress(made.size, total))
        }

        if (made.isEmpty()) {
            throw (lastError as? ApiException)
                ?: ApiException(500, "The AI didn't return usable questions. Try again, or try fewer items.")
        }
        if (made.size < total) {
            notes += "Made ${made.size} of $total items" +
                if (lastError != null) " (${lastError.message?.lineSequence()?.firstOrNull()?.take(120)})." else ". The material may be too short for more."
        }
        // Keep the type order the student picked: MC, then T/F, then identification.
        val ordered = ItemType.entries.flatMap { type -> made.filter { it.type == type } }
        return GenerationResult(ordered, notes)
    }

    private fun fallbackFor(brain: Brain, needsGemini: Boolean): Brain? {
        val s = settings.current
        return when (brain) {
            Brain.GROQ -> if (s.hasGeminiKey) Brain.GEMINI else null
            Brain.GEMINI -> if (!needsGemini && s.hasGroqKey) Brain.GROQ else null
        }
    }

    /** Up to [BATCH] items, filling from the types still missing in order. */
    private fun nextBatch(remaining: Map<ItemType, Int>): Map<ItemType, Int> {
        var room = BATCH
        val batch = linkedMapOf<ItemType, Int>()
        for (type in ItemType.entries) {
            val n = minOf(remaining[type] ?: 0, room)
            if (n > 0) {
                batch[type] = n
                room -= n
            }
        }
        return batch
    }

    private suspend fun ask(
        brain: Brain,
        source: PreparedSource,
        config: ExamConfig,
        batch: Map<ItemType, Int>,
        avoid: List<String>
    ): String {
        val system = ExamPrompt.system(config, avoid)
        return when (brain) {
            Brain.GROQ -> {
                // Plain mode: Groq's strict JSON mode rejects small slips (tested with the
                // default model) and wastes a request; the parser copes with fences and chatter.
                groq.complete(system, ExamPrompt.request(batch, source.text.take(GROQ_MAX_CHARS)))
            }
            Brain.GEMINI -> gemini.generate(buildJsonObject {
                put("systemInstruction", buildJsonObject {
                    put("parts", JsonArray(listOf(buildJsonObject { put("text", system) })))
                })
                put("contents", JsonArray(listOf(buildJsonObject {
                    put("role", "user")
                    put("parts", JsonArray(source.media.map { file ->
                        buildJsonObject {
                            put("inlineData", buildJsonObject {
                                put("mimeType", if (file.mime.startsWith("image/")) file.mime else "application/pdf")
                                put("data", Base64.encodeToString(File(file.path).readBytes(), Base64.NO_WRAP))
                            })
                        }
                    } + buildJsonObject { put("text", ExamPrompt.request(batch, source.text)) }))
                })))
                put("generationConfig", buildJsonObject {
                    put("temperature", 0.6)
                    put("maxOutputTokens", 8192)
                    put("responseMimeType", "application/json")
                })
            })
        }
    }

    private suspend fun waitWithCountdown(ms: Long, made: Int, total: Int, onProgress: (GenerationProgress) -> Unit) {
        var left = (ms / 1000).toInt().coerceAtLeast(1)
        while (left > 0) {
            onProgress(GenerationProgress(made, total, "Waiting for the free limit to reset… ${left}s"))
            delay(1000)
            left--
        }
        onProgress(GenerationProgress(made, total))
    }

    private companion object {
        const val BATCH = 10
        /** Keeps a request inside Groq's free tokens-per-minute limit. */
        const val GROQ_MAX_CHARS = 12_000
        const val MAX_EMPTY_ROUNDS = 3
        const val MAX_WAIT_MS = 65_000L
    }
}
