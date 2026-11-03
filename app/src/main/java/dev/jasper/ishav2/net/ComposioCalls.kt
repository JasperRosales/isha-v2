package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Direct (no-model) Composio calls for background syncs. A project key can
 * run a tool slug straight away; a Connect key only exposes the meta tools,
 * so the call goes through COMPOSIO_MULTI_EXECUTE_TOOL instead.
 */
class ComposioCalls(
    private val settings: SettingsStore,
    private val composio: ComposioManager
) {
    /** Runs the first slug in [slugs] that works and returns its parsed output. */
    suspend fun run(slugs: List<String>, args: JsonObject): JsonElement {
        if (!settings.current.hasComposioKey) throw ApiException(401, "Add a Composio key in Settings first.")
        composio.ensure(force = false)
        var firstError: Exception? = null
        for (slug in slugs) {
            try {
                val raw = when (settings.current.composioMode) {
                    ComposioKeyMode.CONNECT -> composio.execute(
                        "COMPOSIO_MULTI_EXECUTE_TOOL",
                        buildJsonObject {
                            put("tools", buildJsonArray {
                                add(buildJsonObject {
                                    put("tool_slug", slug)
                                    put("arguments", args)
                                })
                            })
                            put("sync_response_to_workbench", false)
                            put("thought", "Background sync on the user's phone.")
                        }
                    )
                    else -> composio.execute(slug, args)
                }
                val parsed = ComposioJson.parse(raw)
                ComposioJson.failure(parsed)?.let { throw ApiException(500, it) }
                return parsed
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (firstError == null) firstError = error
            }
        }
        throw firstError ?: ApiException(500, "Composio call failed.")
    }
}

/** Tolerant readers for Composio output, whose wrapping differs by key type. */
object ComposioJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): JsonElement =
        runCatching { unwrap(json.parseToJsonElement(raw)) }.getOrElse { JsonPrimitive(raw) }

    /** Strings that hold JSON (MCP text content, nested tool output) are parsed in place. */
    fun unwrap(element: JsonElement): JsonElement {
        if (element is JsonPrimitive && element.isString) {
            val text = element.content.trim()
            if (text.startsWith("{") || text.startsWith("[")) {
                return runCatching { unwrap(json.parseToJsonElement(text)) }.getOrDefault(element)
            }
        }
        return element
    }

    /** The error of a call that reported successful=false somewhere obvious, if any. */
    fun failure(element: JsonElement): String? {
        val obj = element as? JsonObject ?: return null
        val ok = (obj["successful"] as? JsonPrimitive)?.booleanOrNull
            ?: (obj["success"] as? JsonPrimitive)?.booleanOrNull
        if (ok == false) {
            return text(obj["error"]) ?: text(obj["message"]) ?: "The app call failed."
        }
        // Multi-execute nests the real result one or two levels down.
        val results = findArray(obj, setOf("results")) ?: return null
        val first = results.firstOrNull() as? JsonObject ?: return null
        val response = (first["response"] as? JsonObject) ?: first
        val nestedOk = (response["successful"] as? JsonPrimitive)?.booleanOrNull
        return if (nestedOk == false) text(response["error"]) ?: "The app call failed." else null
    }

    /** Depth-first search for the first array stored under any of [keys]. */
    fun findArray(element: JsonElement, keys: Set<String>, depth: Int = 0): JsonArray? {
        if (depth > 8) return null
        return when (val node = unwrap(element)) {
            is JsonObject -> {
                for (key in keys) {
                    val value = node[key]?.let(::unwrap)
                    if (value is JsonArray) return value
                }
                node.values.firstNotNullOfOrNull { findArray(it, keys, depth + 1) }
            }
            is JsonArray -> node.firstNotNullOfOrNull { findArray(it, keys, depth + 1) }
            else -> null
        }
    }

    fun text(element: JsonElement?): String? = when (element) {
        null, JsonNull -> null
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }

    fun JsonObject.str(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { text(this[it]) }

    fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    fun JsonObject.obj(key: String): JsonObject? = this[key]?.let { unwrap(it) as? JsonObject }
}
