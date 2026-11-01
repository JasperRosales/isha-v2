package dev.jasper.ishav2.net

import dev.jasper.ishav2.data.AppSettings
import dev.jasper.ishav2.data.ComposioKeyMode
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Chooses REST sessions (ak_ project key) or Connect MCP (ck_ consumer key). */
class ComposioManager(
    private val settings: SettingsStore,
    private val rest: ComposioRest,
    private val mcp: ComposioMcp
) {
    private val _status = MutableStateFlow(ComposioStatus())
    val status: StateFlow<ComposioStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    private var source: RemoteToolSource? = null
    private var sourceKey: String? = null

    @Volatile
    private var tools: List<RemoteTool> = emptyList()

    fun declarations(): List<RemoteTool> = tools

    fun needsApproval(name: String): Boolean = ACTION_TOOL.containsMatchIn(name)

    /**
     * Whether this specific call should pause for the user. A multi-execute
     * batch where every tool only reads (list, get, fetch, search…) runs
     * straight through when "auto-approve reads" is on, so checking the inbox
     * or the calendar never needs a tap. Anything that writes still asks.
     */
    fun needsApproval(name: String, args: JsonObject): Boolean {
        if (!needsApproval(name)) return false
        if (settings.current.autoApproveReads && isReadOnlyBatch(name, args)) return false
        return true
    }

    fun isReadOnlyBatch(name: String, args: JsonObject): Boolean {
        if (!name.contains("MULTI_EXECUTE", ignoreCase = true)) return false
        val tools = args["tools"] as? JsonArray ?: return false
        if (tools.isEmpty()) return false
        return tools.all { entry ->
            val slug = ((entry as? JsonObject)?.get("tool_slug") as? JsonPrimitive)
                ?.contentOrNull
                ?: return@all false
            isReadOnlySlug(slug)
        }
    }

    suspend fun ensure(force: Boolean = false): ComposioStatus = mutex.withLock {
        val current = settings.current
        if (!current.hasComposioKey) {
            source?.reset()
            source = null
            sourceKey = null
            tools = emptyList()
            _status.value = ComposioStatus()
            return@withLock _status.value
        }
        if (current.composioMode == ComposioKeyMode.UNKNOWN) {
            tools = emptyList()
            _status.value = ComposioStatus(
                configured = true,
                mode = ComposioKeyMode.UNKNOWN,
                error = "The Composio key should start with ck_ (Connect) or ak_ (project)."
            )
            return@withLock _status.value
        }

        val wanted = if (current.composioMode == ComposioKeyMode.CONNECT) mcp else rest
        if (source !== wanted || sourceKey != current.composioKey) {
            source?.reset()
            source = wanted
            sourceKey = current.composioKey
            tools = emptyList()
        }
        if (!force && _status.value.connected) return@withLock _status.value

        _status.value = ComposioStatus(
            configured = true,
            connecting = true,
            mode = current.composioMode
        )
        return@withLock try {
            val discovered = wanted.connect(force)
            tools = discovered
            _status.value = ComposioStatus(
                configured = true,
                connected = true,
                mode = current.composioMode,
                toolCount = discovered.size,
                sessionId = settings.current.composioSessionId
            )
            _status.value
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: Exception) {
            tools = emptyList()
            _status.value = ComposioStatus(
                configured = true,
                mode = current.composioMode,
                error = friendly(e)
            )
            _status.value
        }
    }

    suspend fun execute(name: String, args: JsonObject): String {
        val active = source ?: throw ApiException(503, "Composio is not connected.")
        return try {
            active.execute(name, args)
        } catch (error: ApiException) {
            if (error.code == 404 && active.mode == ComposioKeyMode.PROJECT) {
                settings.setComposioSession(null)
                ensure(force = true)
                val retry = source ?: throw error
                retry.execute(name, args)
            } else {
                throw error
            }
        }
    }

    suspend fun accounts(): List<ConnectedAccount> {
        val active = source ?: return emptyList()
        return active.accounts().orEmpty()
    }

    suspend fun startLink(toolkit: String): String? {
        val active = source ?: throw ApiException(503, "Composio is not connected.")
        return active.startLink(toolkit)
    }

    suspend fun catalog(query: String): List<ToolkitInfo> {
        val active = source ?: return emptyList()
        return active.catalog(query)
    }

    fun dashboardUrl(): String = source?.dashboardUrl() ?: "https://connect.composio.dev"

    suspend fun onKeyChanged() {
        reset()
        ensure()
    }

    fun reset() {
        source?.reset()
        source = null
        sourceKey = null
        tools = emptyList()
        _status.value = ComposioStatus()
    }

    fun prettyName(name: String): String = name
        .replace(Regex("^COMPOSIO_"), "")
        .replace('_', ' ')
        .lowercase()
        .replaceFirstChar { it.uppercase() }

    private fun friendly(error: Exception): String {
        if (error is ApiException && (error.code == 401 || error.code == 403)) {
            val hint = when (settings.current.composioMode) {
                ComposioKeyMode.CONNECT ->
                    "Connect expects the consumer key (ck_…) from connect.composio.dev → Settings."
                ComposioKeyMode.PROJECT ->
                    "REST sessions expect a project key (ak_…) from dashboard.composio.dev → Settings → API Keys."
                else -> "Check the key in Settings."
            }
            return "${error.message}\n\n$hint"
        }
        return error.message ?: error.javaClass.simpleName
    }

    companion object {
        private val READ_VERBS = setOf(
            "GET", "LIST", "FETCH", "SEARCH", "FIND", "READ", "RETRIEVE", "QUERY",
            "LOOKUP", "VIEW", "CHECK", "COUNT", "DESCRIBE", "SHOW", "EXPORT"
        )
        private val WRITE_VERBS = setOf(
            "SEND", "CREATE", "DELETE", "REMOVE", "UPDATE", "PATCH", "PUT", "POST",
            "INSERT", "ADD", "MOVE", "MODIFY", "REPLY", "FORWARD", "ARCHIVE", "TRASH",
            "UPLOAD", "SHARE", "INVITE", "ACCEPT", "DECLINE", "CANCEL", "EXECUTE", "RUN",
            "SET", "STAR", "MARK", "APPROVE", "MERGE", "CLOSE", "COMMENT", "EDIT", "WRITE",
            "SUBMIT", "PUBLISH", "SCHEDULE", "ASSIGN", "TRANSFER", "PAY", "BOOK", "LIKE",
            "FOLLOW", "UNFOLLOW", "BLOCK", "REVOKE", "RENAME", "COPY", "DUPLICATE", "ENABLE",
            "DISABLE", "START", "STOP", "TRIGGER", "IMPORT", "BATCH", "MUTE", "PIN", "CLEAR"
        )

        /** GMAIL_FETCH_EMAILS → read-only; GMAIL_SEND_EMAIL → not. Unknown verbs are treated as writes. */
        fun isReadOnlySlug(slug: String): Boolean {
            val words = slug.uppercase().split('_', '-').filter { it.isNotBlank() }
            if (words.any { it in WRITE_VERBS }) return false
            return words.any { it in READ_VERBS }
        }

        private val ACTION_TOOL = Regex("(MULTI_EXECUTE|REMOTE_BASH|REMOTE_WORKBENCH)", RegexOption.IGNORE_CASE)
    }
}

fun AppSettings.hasAnyComposioMode(): Boolean = composioMode != ComposioKeyMode.NONE
