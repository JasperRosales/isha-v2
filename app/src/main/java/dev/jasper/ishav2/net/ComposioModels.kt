package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.ComposioKeyMode
import kotlinx.serialization.json.JsonObject

data class RemoteTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

data class ConnectedAccount(
    val id: String,
    val toolkit: String,
    val status: String,
    val alias: String? = null,
    val createdAt: String? = null
) {
    val toolkitName: String get() = toolkit.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val active: Boolean get() = status.uppercase() == "ACTIVE"
}

data class ToolkitInfo(
    val slug: String,
    val name: String,
    val description: String,
    val logo: String? = null,
    val category: String? = null
)

data class ComposioStatus(
    val configured: Boolean = false,
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val mode: ComposioKeyMode = ComposioKeyMode.NONE,
    val toolCount: Int = 0,
    val error: String? = null,
    val sessionId: String? = null
)

interface RemoteToolSource {
    val mode: ComposioKeyMode
    suspend fun connect(force: Boolean): List<RemoteTool>
    suspend fun execute(name: String, args: JsonObject): String
    suspend fun accounts(): List<ConnectedAccount>?
    suspend fun startLink(toolkit: String): String?
    suspend fun catalog(query: String): List<ToolkitInfo>
    fun dashboardUrl(): String
    fun reset()
}
