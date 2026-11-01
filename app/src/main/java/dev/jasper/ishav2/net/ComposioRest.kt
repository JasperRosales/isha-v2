package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Composio REST v3.1 sessions: project key (ak_) + a stable user id. */
class ComposioRest(private val settings: SettingsStore) : RemoteToolSource {
    override val mode: ComposioKeyMode = ComposioKeyMode.PROJECT

    override suspend fun connect(force: Boolean): List<RemoteTool> = withContext(Dispatchers.IO) {
        if (force) {
            settings.current.composioSessionId?.let { runCatching { deleteSession(it) } }
            settings.setComposioSession(null)
        }
        val existing = settings.current.composioSessionId
        if (existing != null) {
            runCatching { fetchTools(existing) }.getOrNull()?.let { return@withContext it }
        }
        val sessionId = createSession()
        settings.setComposioSession(sessionId)
        fetchTools(sessionId)
    }

    override suspend fun execute(name: String, args: JsonObject): String =
        withContext(Dispatchers.IO) {
            val sessionId = ensureSession()
            val element = request(
                "POST",
                "/tool_router/session/$sessionId/execute",
                buildJsonObject {
                    put("tool_slug", name)
                    put("arguments", args)
                }
            )
            parseExecution(element)
        }

    override suspend fun accounts(): List<ConnectedAccount> = withContext(Dispatchers.IO) {
        val element = request(
            "GET",
            "/connected_accounts",
            query = mapOf("user_ids" to settings.current.userId, "limit" to "100")
        )
        val items = element.jsonObject["items"] as? JsonArray ?: return@withContext emptyList()
        items.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.stringOrNull() ?: return@mapNotNull null
            val toolkit = obj["toolkit"]?.jsonObject?.get("slug")?.stringOrNull() ?: "unknown"
            ConnectedAccount(
                id = id,
                toolkit = toolkit,
                status = obj["status"]?.stringOrNull() ?: "UNKNOWN",
                alias = obj["alias"]?.stringOrNull(),
                createdAt = obj["created_at"]?.stringOrNull()
            )
        }
    }

    override suspend fun startLink(toolkit: String): String? = withContext(Dispatchers.IO) {
        val sessionId = ensureSession()
        val element = request(
            "POST",
            "/tool_router/session/$sessionId/link",
            buildJsonObject { put("toolkit", toolkit) }
        )
        element.jsonObject["redirect_url"]?.stringOrNull()
    }

    override suspend fun catalog(query: String): List<ToolkitInfo> = withContext(Dispatchers.IO) {
        val params = mutableMapOf("limit" to "30")
        if (query.isBlank()) {
            params["sort_by"] = "usage"
        } else {
            params["search"] = query
        }
        val element = request("GET", "/toolkits", query = params)
        val items = element.jsonObject["items"] as? JsonArray ?: return@withContext emptyList()
        items.mapNotNull { item ->
            val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
            val slug = obj["slug"]?.stringOrNull() ?: return@mapNotNull null
            val meta = obj["meta"] as? JsonObject
            val category = (meta?.get("categories") as? JsonArray)?.firstOrNull()?.let { cat ->
                when (cat) {
                    is JsonPrimitive -> cat.contentOrNull
                    is JsonObject -> cat["name"]?.stringOrNull() ?: cat["slug"]?.stringOrNull()
                    else -> null
                }
            }
            ToolkitInfo(
                slug = slug,
                name = obj["name"]?.stringOrNull() ?: slug,
                description = meta?.get("description")?.stringOrNull().orEmpty(),
                logo = meta?.get("logo")?.stringOrNull(),
                category = category
            )
        }
    }

    override fun dashboardUrl(): String = "https://dashboard.composio.dev"

    override fun reset() = Unit

    suspend fun validate() = withContext(Dispatchers.IO) {
        request("GET", "/connected_accounts", query = mapOf("limit" to "1"))
        Unit
    }

    private suspend fun ensureSession(): String {
        settings.current.composioSessionId?.let { return it }
        val sessionId = createSession()
        settings.setComposioSession(sessionId)
        return sessionId
    }

    private suspend fun createSession(): String {
        val userId = settings.current.userId
        val element = request(
            "POST",
            "/tool_router/session",
            buildJsonObject {
                put("user_id", userId)
                put(
                    "manage_connections",
                    buildJsonObject {
                        put("enable", true)
                        put("enable_connection_removal", false)
                        put("enable_wait_for_connections", false)
                    }
                )
                put("workbench", buildJsonObject { put("enable", false) })
                put(
                    "experimental",
                    buildJsonObject {
                        put(
                            "permissions",
                            buildJsonObject { put("default", "allow_all") }
                        )
                    }
                )
            }
        )
        return element.jsonObject["session_id"]?.stringOrNull()
            ?: throw ApiException(500, "Composio did not return a session id.")
    }

    private suspend fun deleteSession(sessionId: String) {
        request("DELETE", "/tool_router/session/$sessionId")
    }

    private suspend fun fetchTools(sessionId: String): List<RemoteTool> {
        val found = mutableListOf<RemoteTool>()
        var cursor: String? = null
        for (page in 0 until 5) {
            val query = mutableMapOf("limit" to "500")
            if (cursor != null) query["cursor"] = cursor
            val element = request("GET", "/tool_router/session/$sessionId/tools", query = query)
            val obj = element.jsonObject
            val items = obj["items"] as? JsonArray ?: break
            for (item in items) {
                val tool = runCatching { item.jsonObject }.getOrNull() ?: continue
                val slug = tool["slug"]?.stringOrNull() ?: continue
                val description = tool["description"]?.stringOrNull()
                    ?: tool["human_description"]?.stringOrNull()
                    ?: "Composio tool $slug"
                val schema = tool["input_parameters"] as? JsonObject ?: buildJsonObject { }
                found += RemoteTool(slug, description, schema)
            }
            cursor = obj["next_cursor"]?.stringOrNull()
            if (cursor.isNullOrBlank()) break
        }
        if (found.isEmpty()) throw ApiException(500, "Composio returned no tools for this session.")
        return found
    }

    private fun parseExecution(element: JsonElement): String {
        val obj = element as? JsonObject ?: return element.toString()
        return when (obj["result_type"]?.stringOrNull()) {
            "completed" -> jsonText(obj["data"])
            "failed" -> {
                val error = obj["error"]?.stringOrNull()
                val data = obj["data"]?.let { jsonText(it) }
                throw ApiException(500, error ?: data ?: "Composio tool failed.")
            }
            "blocked" -> throw ApiException(
                403,
                obj["message"]?.stringOrNull() ?: "A Composio hook blocked this call."
            )
            "input_required" -> {
                val requests = obj["input_requests"]?.let { jsonText(it) }
                throw ApiException(
                    409,
                    "This app action needs more input:\n${requests ?: "(no details)"}"
                )
            }
            else -> jsonText(element)
        }
    }

    private fun jsonText(element: JsonElement?): String = when (element) {
        null, JsonNull -> "(no output)"
        is JsonPrimitive -> element.contentOrNull ?: element.toString()
        else -> element.toString()
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        query: Map<String, String> = emptyMap()
    ): JsonElement {
        val key = settings.current.composioKey
        if (key.isBlank()) throw ApiException(401, "Add a Composio key in Settings.")
        val base = "https://backend.composio.dev/api/v3.1$path".toHttpUrlOrNull()
            ?: throw ApiException(400, "Bad Composio URL.")
        val url = base.newBuilder().apply {
            for ((name, value) in query) addQueryParameter(name, value)
        }.build()
        val builder = Request.Builder()
            .url(url)
            .header("x-api-key", key)
            .header("accept", "application/json")
        val requestBody = body?.let { Http.body(it) } ?: "".toRequestBody(Http.mediaTypeJson)
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody)
        val call = Http.client.newCall(builder.build())
        call.timeout().timeout(90, java.util.concurrent.TimeUnit.SECONDS)
        val response = call.awaitResponse()
        response.use {
            val text = it.body?.string()
            if (!it.isSuccessful) {
                throw ApiException(it.code, friendly(it.code, Http.errorMessage(text)))
            }
            if (text.isNullOrBlank()) return JsonNull
            return Http.json.parseToJsonElement(text)
        }
    }

    private fun friendly(code: Int, detail: String): String {
        val prefix = "Composio ($code)"
        return when (code) {
            401, 403 -> "$prefix rejected the key. Use a project key (ak_…) from dashboard.composio.dev. $detail"
            402 -> "$prefix: this feature needs a paid Composio plan. $detail"
            429 -> "$prefix is rate-limiting requests. Try again shortly. $detail"
            404 -> "$prefix: not found. The session or connected account may have expired. $detail"
            else -> "$prefix: $detail"
        }
    }

    private fun JsonElement.stringOrNull(): String? =
        runCatching { jsonPrimitive.contentOrNull }.getOrNull()
}
