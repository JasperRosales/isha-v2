package dev.jasper.ishav2.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Minimal MCP "Streamable HTTP" client: initialize, tools/list, tools/call.
 * Handles JSON and SSE responses, session ids and one reconnect on expiry.
 */
class McpClient(
    private val url: String,
    private val apiKey: String,
    private val name: String = "isha-android",
    private val version: String = "0.1.0"
) {
    private var sessionId: String? = null

    /** Connect MCP is stateless: no session id does not mean "not initialized". */
    @Volatile
    private var initialized = false

    private var protocolVersion = PROTOCOL_VERSION
    private var nextId = 0
    private val connectMutex = Mutex()

    suspend fun connect() {
        if (initialized) return
        connectMutex.withLock {
            if (initialized) return
            val id = ++nextId
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", "initialize")
                put(
                    "params",
                    buildJsonObject {
                        put("protocolVersion", PROTOCOL_VERSION)
                        put("capabilities", buildJsonObject { })
                        put(
                            "clientInfo",
                            buildJsonObject {
                                put("name", name)
                                put("version", version)
                            }
                        )
                    }
                )
            }
            val reply = post(body, withSession = false)
            val result = result(reply.messages, id)
            protocolVersion = result["protocolVersion"]?.contentOrNull() ?: PROTOCOL_VERSION
            sessionId = reply.sessionId
            notify("notifications/initialized")
            initialized = true
        }
    }

    suspend fun listTools(): List<RemoteTool> {
        connect()
        val found = mutableListOf<RemoteTool>()
        var cursor: String? = null
        for (page in 0 until 20) {
            val id = ++nextId
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", "tools/list")
                put(
                    "params",
                    buildJsonObject {
                        if (cursor != null) put("cursor", cursor)
                    }
                )
            }
            val reply = post(body, withSession = true)
            val result = result(reply.messages, id)
            val tools = result["tools"] as? JsonArray ?: JsonArray(emptyList())
            for (item in tools) {
                val tool = runCatching { item.jsonObject }.getOrNull() ?: continue
                val toolName = tool["name"]?.contentOrNull() ?: continue
                val description = tool["description"]?.contentOrNull()
                    ?: "Composio tool $toolName"
                val schema = tool["inputSchema"] as? JsonObject ?: buildJsonObject { }
                found += RemoteTool(toolName, description.take(4000), schema)
            }
            cursor = result["nextCursor"]?.contentOrNull()
            if (cursor.isNullOrBlank()) break
        }
        return found
    }

    suspend fun callTool(toolName: String, args: JsonObject): String {
        connect()
        val id = ++nextId
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            put(
                "params",
                buildJsonObject {
                    put("name", toolName)
                    put("arguments", args)
                }
            )
        }
        val reply = post(body, withSession = true)
        val result = result(reply.messages, id)
        val chunks = mutableListOf<String>()
        (result["content"] as? JsonArray)?.forEach { part ->
            val obj = runCatching { part.jsonObject }.getOrNull()
            when (obj?.get("type")?.contentOrNull()) {
                "text" -> chunks += obj["text"]?.contentOrNull().orEmpty()
                "resource" -> {
                    val resource = obj["resource"] as? JsonObject
                    chunks += resource?.get("text")?.contentOrNull() ?: resource?.toString().orEmpty()
                }
                "image" -> chunks += "[image ${obj["mimeType"]?.contentOrNull() ?: "image"}]"
                else -> if (obj != null) chunks += obj.toString()
            }
        }
        if (chunks.isEmpty()) {
            (result["structuredContent"] as? JsonElement)?.let { chunks += it.toString() }
        }
        val text = chunks.joinToString("\n").trim().ifBlank { "(no output)" }
        if (result["isError"]?.jsonPrimitive?.contentOrNull?.toBoolean() == true) {
            throw ApiException(500, text.take(1200))
        }
        return text
    }

    fun reset() {
        sessionId = null
        initialized = false
    }

    suspend fun close() {
        val id = sessionId
        sessionId = null
        initialized = false
        if (id == null) return
        val request = Request.Builder()
            .url(url)
            .headers(headers(id))
            .delete()
            .build()
        runCatching {
            Http.client.newCall(request).awaitResponse().close()
        }
    }

    private suspend fun notify(method: String) {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
        }
        val request = Request.Builder()
            .url(url)
            .headers(headers(sessionId))
            .post(Http.body(body))
            .build()
        val call = Http.client.newCall(request)
        call.timeout().timeout(60, TimeUnit.SECONDS)
        val response = call.awaitResponse()
        response.use {
            if (!it.isSuccessful) {
                throw ApiException(
                    it.code,
                    "MCP notification failed (HTTP ${it.code})."
                )
            }
        }
    }

    private suspend fun post(
        body: JsonObject,
        withSession: Boolean,
        allowRetry: Boolean = true
    ): PostResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(headers(if (withSession) sessionId else null))
            .post(Http.body(body))
            .build()
        val call = Http.client.newCall(request)
        call.timeout().timeout(90, TimeUnit.SECONDS)
        val response = call.awaitResponse()
        response.use { resp ->
            val id = body["id"]?.jsonPrimitive?.intOrNull
            if (allowRetry && (resp.code == 404 || resp.code == 400) && withSession && sessionId != null) {
                sessionId = null
                initialized = false
                connect()
                return@withContext post(body, withSession = true, allowRetry = false)
            }
            val messages = readMessages(resp, id)
            if (!resp.isSuccessful) {
                val detail = messages.firstNotNullOfOrNull { message ->
                    (message["error"] as? JsonObject)?.get("message")?.contentOrNull()
                } ?: resp.message
                if (resp.code == 401 || resp.code == 403) {
                    throw ApiException(
                        resp.code,
                        "Composio Connect rejected the consumer key (ck_…). $detail"
                    )
                }
                throw ApiException(
                    resp.code,
                    "MCP server error (HTTP ${resp.code}): ${detail.take(400)}"
                )
            }
            PostResult(messages, resp.header("mcp-session-id"))
        }
    }

    private fun result(messages: List<JsonObject>, id: Int): JsonObject {
        val reply = messages.firstOrNull {
            runCatching { it["id"]?.jsonPrimitive?.intOrNull == id }.getOrDefault(false)
        } ?: messages.firstOrNull { it.containsKey("id") }
        ?: throw ApiException(502, "The MCP server returned no response.")
        (reply["error"] as? JsonObject)?.let { error ->
            val code = error["code"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            val message = error["message"]?.contentOrNull() ?: "request failed"
            throw ApiException(500, "MCP ${code ?: ""}: $message".trim())
        }
        return reply["result"] as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun headers(session: String?): Headers {
        val builder = Headers.Builder()
            .add("content-type", "application/json")
            .add("accept", "application/json, text/event-stream")
            .add("x-consumer-api-key", apiKey)
        if (session != null) {
            builder.add("mcp-session-id", session)
            builder.add("mcp-protocol-version", protocolVersion)
        }
        return builder.build()
    }

    private fun readMessages(response: Response, id: Int?): List<JsonObject> {
        val type = response.header("content-type").orEmpty()
        if (type.contains("text/event-stream")) return readSse(response, id)
        val text = response.body?.string().orEmpty()
        if (text.isBlank()) return emptyList()
        if (Regex("(^|\\n)\\s*(event|data):").containsMatchIn(text)) {
            return parseSseText(text)
        }
        return parseJsonMessages(text)
    }

    private fun readSse(response: Response, id: Int?): List<JsonObject> {
        val source = response.body?.source() ?: return emptyList()
        val messages = mutableListOf<JsonObject>()
        val data = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    parseRpc(data.toString())?.let { message ->
                        messages += message
                        if (id != null && messageId(message) == id) return messages
                    }
                    data.clear()
                }
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
        if (data.isNotEmpty()) {
            parseRpc(data.toString())?.let { messages += it }
        }
        return messages
    }

    private fun parseSseText(text: String): List<JsonObject> {
        val messages = mutableListOf<JsonObject>()
        for (block in text.split(Regex("\\r?\\n\\r?\\n"))) {
            val data = block.lineSequence()
                .filter { it.startsWith("data:") }
                .joinToString("\n") { it.removePrefix("data:").trimStart() }
            if (data.isNotBlank()) parseRpc(data)?.let { messages += it }
        }
        return messages
    }

    private fun parseJsonMessages(text: String): List<JsonObject> {
        return runCatching {
            when (val element = Http.json.parseToJsonElement(text)) {
                is JsonArray -> element.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(element)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRpc(text: String): JsonObject? =
        runCatching { Http.json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun messageId(message: JsonObject): Int? =
        runCatching { message["id"]?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun JsonElement.contentOrNull(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    private data class PostResult(val messages: List<JsonObject>, val sessionId: String?)

    private companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
    }
}
