package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiClient(private val settings: SettingsStore) {
    /**
     * Streaming needs a long-lived response. The shared client has no read
     * timeout (fine for MCP long-polls); without a finite one here a stalled
     * Gemini connection would leave the chat on "thinking" forever.
     */
    private val streamClient = Http.client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun stream(request: GeminiRequest, onChunk: (GeminiChunk) -> Unit) =
        withContext(Dispatchers.IO) {
            val key = settings.current.geminiKey
            if (key.isBlank()) throw ApiException(401, "Add a Gemini API key in Settings.")
            val model = settings.current.model.ifBlank { "gemini-3.5-flash-lite" }
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
            val body = Http.json.encodeToString(request).toRequestBody(Http.mediaTypeJson)
            val call = streamClient.newCall(
                Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", key)
                    .post(body)
                    .build()
            )
            val job = currentCoroutineContext()[Job]
            val handle = call.cancelWith(job)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val text = response.body?.string()
                        throw ApiException(
                            response.code,
                            friendlyGeminiError(response.code, Http.errorMessage(text)),
                            retryAfterMs = if (response.code == 429) quotaBackoffMs(text) else null
                        )
                    }
                    val source = response.body?.source()
                        ?: throw ApiException(response.code, "Gemini returned an empty response.")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank() || data == "[DONE]") continue
                        val chunk = runCatching {
                            Http.json.decodeFromString<GeminiChunk>(data)
                        }.getOrNull() ?: continue
                        chunk.error?.let { throw ApiException(it.code ?: 500, it.message) }
                        onChunk(chunk)
                    }
                }
            } finally {
                handle?.dispose()
            }
        }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val key = settings.current.geminiKey
        if (key.isBlank()) throw ApiException(401, "Add a Gemini API key first.")
        val call = Http.client.newCall(
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
                .header("x-goog-api-key", key)
                .get()
                .build()
        )
        call.timeout().timeout(30, java.util.concurrent.TimeUnit.SECONDS)
        call.awaitResponse().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful) {
                throw ApiException(response.code, friendlyGeminiError(response.code, Http.errorMessage(text)))
            }
            val parsed = Http.json.decodeFromString<GeminiModelsResponse>(text.orEmpty())
            parsed.models
                .filter { it.supportedMethods.contains("generateContent") }
                .map { it.name.substringAfterLast('/') }
                .filter { it.isNotBlank() }
                .sorted()
        }
    }

    /**
     * How long to skip Gemini after a 429. A spent daily free-tier quota only
     * resets at midnight Pacific; per-minute limits say how long to wait.
     */
    /**
     * One non-streaming generateContent call with a raw request body, for background
     * jobs like writing exam items. Returns the reply text without thoughts.
     */
    suspend fun generate(request: kotlinx.serialization.json.JsonObject): String = withContext(Dispatchers.IO) {
        val key = settings.current.geminiKey
        if (key.isBlank()) throw ApiException(401, "Add a Gemini API key in Settings.")
        val model = settings.current.model.ifBlank { "gemini-3.5-flash-lite" }
        val call = streamClient.newCall(
            Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(Http.body(request))
                .build()
        )
        val job = currentCoroutineContext()[Job]
        val handle = call.cancelWith(job)
        try {
            call.execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException(
                        response.code,
                        friendlyGeminiError(response.code, Http.errorMessage(text)),
                        retryAfterMs = if (response.code == 429) quotaBackoffMs(text) else null
                    )
                }
                val chunk = Http.json.decodeFromString<GeminiChunk>(text.orEmpty())
                chunk.promptFeedback?.blockReason?.let { throw ApiException(400, "Gemini blocked this material ($it).") }
                chunk.candidates.firstOrNull()?.content?.parts.orEmpty()
                    .filter { it.thought != true }
                    .mapNotNull { it.text }
                    .joinToString("")
                    .ifBlank { throw ApiException(500, "Gemini returned an empty reply.") }
            }
        } finally {
            handle?.dispose()
        }
    }

    private fun quotaBackoffMs(body: String?): Long {
        val text = body.orEmpty()
        val now = System.currentTimeMillis()
        if (text.contains("PerDay", ignoreCase = true) || text.contains("per day", ignoreCase = true)) {
            val pacific = java.time.ZoneId.of("America/Los_Angeles")
            val reset = java.time.ZonedDateTime.now(pacific)
                .toLocalDate().plusDays(1).atStartOfDay(pacific)
                .toInstant().toEpochMilli()
            return (reset - now).coerceAtLeast(60_000)
        }
        val seconds = Regex(""""retryDelay"\s*:\s*"(\d+(?:\.\d+)?)s"""").find(text)
            ?.groupValues?.get(1)?.toDoubleOrNull()
            ?: Regex("""retry in (\d+(?:\.\d+)?)\s*s""", RegexOption.IGNORE_CASE).find(text)
                ?.groupValues?.get(1)?.toDoubleOrNull()
        return ((seconds ?: 60.0) * 1000).toLong().coerceIn(10_000, 3_600_000)
    }

    private fun friendlyGeminiError(code: Int, detail: String): String = when (code) {
        400 -> if (detail.contains("API key", ignoreCase = true)) {
            "Gemini rejected the API key. Re-copy it from aistudio.google.com/apikey."
        } else {
            detail
        }
        401, 403 -> "Gemini rejected the API key. Check it in Settings."
        404 -> "That Gemini model is not available for this key. Try another model in Settings."
        429 -> "Gemini's free-tier limit is full right now. Wait a minute and try again.\n\n$detail"
        else -> "Gemini error ($code): $detail"
    }
}
