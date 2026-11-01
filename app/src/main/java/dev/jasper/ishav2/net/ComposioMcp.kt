package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Composio Connect MCP: consumer key (ck_) for an already-connected account. */
class ComposioMcp(private val settings: SettingsStore) : RemoteToolSource {
    override val mode: ComposioKeyMode = ComposioKeyMode.CONNECT

    private var client: McpClient? = null
    private var clientKey: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun connect(force: Boolean): List<RemoteTool> {
        val key = settings.current.composioKey
        if (force) reset()
        val active = client ?: McpClient(URL, key).also {
            client = it
            clientKey = key
        }
        if (clientKey != key) {
            reset()
            return connect(force)
        }
        active.connect()
        val tools = active.listTools()
        if (tools.isEmpty()) throw ApiException(500, "Composio Connect returned no tools.")
        return tools
    }

    override suspend fun execute(name: String, args: JsonObject): String {
        val active = client ?: throw ApiException(503, "Composio Connect is not initialized.")
        return active.callTool(name, args)
    }

    override suspend fun accounts(): List<ConnectedAccount>? = null

    override suspend fun startLink(toolkit: String): String? = null

    override suspend fun catalog(query: String): List<ToolkitInfo> = emptyList()

    override fun dashboardUrl(): String = "https://connect.composio.dev"

    override fun reset() {
        val old = client
        client = null
        clientKey = null
        // The session is server-side; best-effort DELETE without blocking the caller.
        if (old != null) {
            scope.launch { runCatching { old.close() } }
        }
    }

    private companion object {
        const val URL = "https://connect.composio.dev/mcp"
    }
}
