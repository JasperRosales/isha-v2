package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A finished tool call from Groq. */
data class GroqToolCall(val id: String, val name: String, val argsJson: String)

/**
 * Streaming client for Groq's OpenAI-compatible Chat Completions API. Groq is
 * isha's main brain; Gemini takes over when Groq's free limits run out.
 * Message/tool JSON is built by [dev.jasper.ishav2.agent.AgentRunner].
 */
class GroqClient(private val settings: SettingsStore) {
    private val streamClient = Http.client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val model: String get() = settings.current.groqModel.ifBlank { AppSettings.DEFAULT_GROQ_MODEL }

    /** Streams one assistant turn. Returns the finish_reason. */
    suspend fun stream(
        messages: JsonArray,
        tools: JsonArray?,
        onText: (String) -> Unit,
        onToolCall: (GroqToolCall) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val key = settings.current.groqKey
        if (key.isBlank()) throw ApiException(401, "Add a Groq API key in Settings.")
        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            put("temperature", 0.7)
            put("max_completion_tokens", 8192)
            put("messages", messages)
            if (tools != null && tools.isNotEmpty()) put("tools", tools)
        }
        val call = streamClient.newCall(
            Request.Builder()
                .url("$BASE/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(Http.body(body))
                .build()
        )
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { runCatching { call.cancel() } }

        // Tool calls arrive by index; name/arguments may be split across chunks.
        val open = sortedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
        var finishReason: String? = null
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string()
                    throw ApiException(
                        response.code,
                        friendlyError(response.code, Http.errorMessage(text)),
                        retryAfterMs = backoffMs(response.code, response.header("retry-after"), text)
                    )
                }
                val source = response.body?.source()
                    ?: throw ApiException(response.code, "Groq returned an empty response.")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") continue
                    val event = runCatching { Http.json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    (event["error"] as? JsonObject)?.let { error ->
                        val message = error.str("message") ?: "unknown"
                        val code = if (error.str("code") == "rate_limit_exceeded") 429 else 500
                        throw ApiException(code, "Groq error: $message", retryAfterMs = if (code == 429) 60_000 else null)
                    }
                    val choice = runCatching { event["choices"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull() ?: continue
                    choice.str("finish_reason")?.let { finishReason = it }
                    val delta = choice["delta"] as? JsonObject ?: continue
                    delta.str("content")?.takeIf { it.isNotEmpty() }?.let(onText)
                    val toolCalls = runCatching { delta["tool_calls"]?.jsonArray }.getOrNull() ?: continue
                    for (element in toolCalls) {
                        val tc = element as? JsonObject ?: continue
                        val index = runCatching { tc["index"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: open.size
                        val slot = open.getOrPut(index) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                        tc.str("id")?.let { if (slot.first.isEmpty()) slot.first.append(it) }
                        val fn = tc["function"] as? JsonObject
                        fn?.str("name")?.let { slot.second.append(it) }
                        fn?.str("arguments")?.let { slot.third.append(it) }
                    }
                }
            }
        } finally {
            handle?.dispose()
        }
        for ((id, name, args) in open.values) {
            if (name.isNotBlank()) onToolCall(GroqToolCall(id.toString(), name.toString(), args.toString().ifBlank { "{}" }))
        }
        finishReason
    }

    /**
     * One non-streaming completion with no tools, for background jobs like writing
     * exam items. With [json] the reply is constrained to a JSON object.
     */
    suspend fun complete(system: String, user: String, json: Boolean = false, maxTokens: Int = 8192): String =
        withContext(Dispatchers.IO) {
            val key = settings.current.groqKey
            if (key.isBlank()) throw ApiException(401, "Add a Groq API key in Settings.")
            val body = buildJsonObject {
                put("model", model)
                put("temperature", 0.6)
                put("max_completion_tokens", maxTokens)
                put("messages", JsonArray(listOf(
                    buildJsonObject { put("role", "system"); put("content", system) },
                    buildJsonObject { put("role", "user"); put("content", user) }
                )))
                if (json) put("response_format", buildJsonObject { put("type", "json_object") })
            }
            val call = streamClient.newCall(
                Request.Builder()
                    .url("$BASE/chat/completions")
                    .header("Authorization", "Bearer $key")
                    .post(Http.body(body))
                    .build()
            )
            val job = currentCoroutineContext()[Job]
            val handle = job?.invokeOnCompletion { runCatching { call.cancel() } }
            try {
                call.execute().use { response ->
                    val text = response.body?.string()
                    if (!response.isSuccessful) {
                        throw ApiException(
                            response.code,
                            friendlyError(response.code, Http.errorMessage(text)),
                            retryAfterMs = backoffMs(response.code, response.header("retry-after"), text)
                        )
                    }
                    val root = Http.json.parseToJsonElement(text.orEmpty()).jsonObject
                    val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message") as? JsonObject
                    message?.str("content") ?: throw ApiException(500, "Groq returned an empty reply.")
                }
            } finally {
                handle?.dispose()
            }
        }

    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val key = settings.current.groqKey
        if (key.isBlank()) throw ApiException(401, "Add a Groq API key first.")
        val call = Http.client.newCall(
            Request.Builder()
                .url("$BASE/models")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
        )
        call.timeout().timeout(30, TimeUnit.SECONDS)
        call.awaitResponse().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful) {
                throw ApiException(response.code, friendlyError(response.code, Http.errorMessage(text)))
            }
            val data = Http.json.parseToJsonElement(text.orEmpty()).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
            data.mapNotNull { (it as? JsonObject)?.str("id") }
                .filterNot { id -> NON_CHAT.any { id.contains(it, ignoreCase = true) } }
                .sorted()
        }
    }

    /**
     * How long to skip Groq after a limit error. Groq sends retry-after in
     * seconds; a spent daily token limit says "per day" and gets a long pause.
     */
    private fun backoffMs(code: Int, retryAfter: String?, body: String?): Long? {
        if (code != 429 && code != 413) return null
        val text = body.orEmpty()
        val header = retryAfter?.trim()?.toDoubleOrNull()
        if (header != null) return (header * 1000).toLong().coerceIn(5_000, 6 * 3_600_000L)
        val parsed = Regex("""try again in (?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s)?""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues
        if (parsed != null) {
            val ms = (parsed[1].toLongOrNull() ?: 0) * 3_600_000 +
                (parsed[2].toLongOrNull() ?: 0) * 60_000 +
                ((parsed[3].toDoubleOrNull() ?: 0.0) * 1000).toLong()
            if (ms > 0) return ms.coerceIn(5_000, 6 * 3_600_000L)
        }
        if (text.contains("per day", ignoreCase = true) || text.contains("TPD") || text.contains("RPD")) {
            return 3_600_000L
        }
        return 60_000L
    }

    private fun friendlyError(code: Int, detail: String): String = when (code) {
        401, 403 -> "Groq rejected the API key. Check it in Settings."
        404 -> "The Groq model \"$model\" is not available for this key. Pick another in Settings."
        413 -> "This chat is too large for Groq's free limit right now.\n\n$detail"
        429 -> "Groq's free limit is full right now. Wait a minute and try again.\n\n$detail"
        else -> "Groq error ($code): $detail"
    }

    private fun JsonObject.str(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val BASE = "https://api.groq.com/openai/v1"
        val NON_CHAT = listOf("whisper", "orpheus", "prompt-guard", "safeguard", "tts", "playai")
    }
}
