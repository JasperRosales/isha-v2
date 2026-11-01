package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A finished tool_use block from Claude. */
data class ClaudeToolUse(val id: String, val name: String, val input: JsonObject)

/**
 * Minimal streaming client for Anthropic's Messages API. isha uses it as the
 * backup brain when Gemini's free quota is used up (or Gemini is down).
 * Message/tool JSON is built by [dev.jasper.ishav2.agent.AgentRunner].
 */
class ClaudeClient(private val settings: SettingsStore) {
    private val streamClient = Http.client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val model: String get() = settings.current.claudeModel.ifBlank { AppSettings.DEFAULT_CLAUDE_MODEL }

    /** Streams one assistant turn. Returns Claude's stop_reason. */
    suspend fun stream(
        system: String,
        messages: JsonArray,
        tools: JsonArray?,
        onText: (String) -> Unit,
        onToolUse: (ClaudeToolUse) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val key = settings.current.claudeKey
        if (key.isBlank()) throw ApiException(401, "Add a Claude API key in Settings.")
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            put("stream", true)
            put("system", system)
            put("messages", messages)
            if (tools != null && tools.isNotEmpty()) put("tools", tools)
        }
        val call = streamClient.newCall(
            Request.Builder()
                .url(URL)
                .header("x-api-key", key)
                .header("anthropic-version", API_VERSION)
                .post(Http.body(body))
                .build()
        )
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { runCatching { call.cancel() } }

        // Open tool_use blocks by stream index; input arrives as JSON fragments.
        val openTools = mutableMapOf<Int, Triple<String, String, StringBuilder>>()
        var stopReason: String? = null
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string()
                    throw ApiException(response.code, friendlyError(response.code, Http.errorMessage(text)))
                }
                val source = response.body?.source()
                    ?: throw ApiException(response.code, "Claude returned an empty response.")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) continue
                    val event = runCatching { Http.json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    when (event.str("type")) {
                        "content_block_start" -> {
                            val block = event["content_block"] as? JsonObject ?: continue
                            val index = event.int("index") ?: continue
                            if (block.str("type") == "tool_use") {
                                openTools[index] = Triple(block.str("id").orEmpty(), block.str("name").orEmpty(), StringBuilder())
                            }
                        }
                        "content_block_delta" -> {
                            val delta = event["delta"] as? JsonObject ?: continue
                            when (delta.str("type")) {
                                "text_delta" -> delta.str("text")?.let(onText)
                                "input_json_delta" -> {
                                    val index = event.int("index") ?: continue
                                    openTools[index]?.third?.append(delta.str("partial_json").orEmpty())
                                }
                            }
                        }
                        "content_block_stop" -> {
                            val index = event.int("index") ?: continue
                            val (id, name, json) = openTools.remove(index) ?: continue
                            val input = runCatching {
                                Http.json.parseToJsonElement(json.toString().ifBlank { "{}" }).jsonObject
                            }.getOrElse { buildJsonObject { } }
                            if (name.isNotBlank()) onToolUse(ClaudeToolUse(id, name, input))
                        }
                        "message_delta" -> {
                            (event["delta"] as? JsonObject)?.str("stop_reason")?.let { stopReason = it }
                        }
                        "error" -> {
                            val error = event["error"] as? JsonObject
                            val type = error?.str("type")
                            val code = when (type) {
                                "overloaded_error" -> 529
                                "rate_limit_error" -> 429
                                else -> 500
                            }
                            throw ApiException(code, "Claude error: ${error?.str("message") ?: type ?: "unknown"}")
                        }
                    }
                }
            }
        } finally {
            handle?.dispose()
        }
        stopReason
    }

    private fun friendlyError(code: Int, detail: String): String = when (code) {
        401, 403 -> "Claude rejected the API key. Check it in Settings."
        404 -> "The Claude model \"$model\" is not available for this key. Pick another in Settings."
        429 -> "Claude's rate limit is full right now. Wait a minute and try again.\n\n$detail"
        529 -> "Claude is overloaded right now. Try again in a moment."
        else -> if (detail.contains("credit balance", ignoreCase = true)) {
            "Your Anthropic credit balance is too low for Claude. Add credits at console.anthropic.com."
        } else {
            "Claude error ($code): $detail"
        }
    }

    private fun JsonObject.str(name: String): String? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(name: String): Int? =
        runCatching { this[name]?.jsonPrimitive?.intOrNull }.getOrNull()

    private companion object {
        const val URL = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
    }
}
