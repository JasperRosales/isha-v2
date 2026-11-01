package dev.jasper.ishav2.agent

import android.util.Base64
import dev.jasper.ishav2.data.ApprovalMode
import dev.jasper.ishav2.data.BrainMode
import dev.jasper.ishav2.data.MemoryStore
import dev.jasper.ishav2.local.InstalledModel
import dev.jasper.ishav2.local.LocalLlm
import dev.jasper.ishav2.local.LocalModelManager
import dev.jasper.ishav2.util.NetworkMonitor
import java.io.IOException
import dev.jasper.ishav2.data.ChatMessage
import dev.jasper.ishav2.data.Conversation
import dev.jasper.ishav2.data.MessageStatus
import dev.jasper.ishav2.data.Part
import dev.jasper.ishav2.data.Role
import dev.jasper.ishav2.data.SettingsStore
import dev.jasper.ishav2.data.ToolStatus
import dev.jasper.ishav2.data.plainText
import dev.jasper.ishav2.net.ApiException
import dev.jasper.ishav2.net.ComposioManager
import dev.jasper.ishav2.net.GeminiChunk
import dev.jasper.ishav2.net.GeminiClient
import dev.jasper.ishav2.net.GeminiContent
import dev.jasper.ishav2.net.GeminiFunctionCall
import dev.jasper.ishav2.net.GeminiFunctionDeclaration
import dev.jasper.ishav2.net.GeminiFunctionResponse
import dev.jasper.ishav2.net.GeminiGenerationConfig
import dev.jasper.ishav2.net.GeminiInlineData
import dev.jasper.ishav2.net.GeminiPart
import dev.jasper.ishav2.net.GeminiRequest
import dev.jasper.ishav2.net.GeminiTool
import dev.jasper.ishav2.net.Http
import dev.jasper.ishav2.net.SchemaSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

class AgentRunner(
    private val settings: SettingsStore,
    private val gemini: GeminiClient,
    private val composio: ComposioManager,
    private val nativeTools: NativeTools,
    private val approvals: ApprovalBroker,
    private val memory: MemoryStore,
    private val models: LocalModelManager,
    private val localLlm: LocalLlm,
    private val network: NetworkMonitor,
    private val budgetAssistant: BudgetAssistant
) {
    interface Listener {
        fun onUpdate()
        suspend fun onPersist()
    }

    /** Which brain the next reply will use, for the chat header. */
    fun plannedBrain(): Brain {
        val current = settings.current
        val local = models.active()
        return when (current.brainMode) {
            BrainMode.OFFLINE -> if (local != null) Brain.Local(local) else Brain.Missing(
                "Offline mode is on, but no on-device model is selected. Download one in Offline models."
            )
            BrainMode.ONLINE -> if (current.hasGeminiKey) Brain.Gemini else Brain.Missing(
                "Add a Gemini API key in Settings, or switch to offline mode."
            )
            BrainMode.AUTO -> when {
                current.hasGeminiKey && network.isOnline() -> Brain.Gemini
                local != null -> Brain.Local(local)
                current.hasGeminiKey -> Brain.Gemini
                else -> Brain.Missing("Add a Gemini API key, or download an offline model.")
            }
        }
    }

    /**
     * @param interactive false for background runs (daily briefing): nobody is
     * there to tap Approve, so calls that need approval are declined instead
     * of waiting.
     */
    suspend fun run(
        conversation: Conversation,
        listener: Listener,
        interactive: Boolean = true
    ) {
        // The user's turn was already appended by the caller so the UI reacts on
        // tap. Persist it before any network work so it survives a crash.
        listener.onPersist()

        when (val brain = plannedBrain()) {
            is Brain.Local -> {
                if (answerBudgetOnDevice(conversation, listener)) return
                return runLocal(conversation, brain.model, listener, note = null)
            }
            is Brain.Missing -> {
                // Budget commands never need a model: "spent 150 on lunch" works anywhere.
                if (answerBudgetOnDevice(conversation, listener)) return
                conversation.messages += ChatMessage(
                    role = Role.MODEL,
                    status = MessageStatus.ERROR,
                    error = brain.reason
                )
                listener.onUpdate()
                listener.onPersist()
                return
            }
            Brain.Gemini -> Unit
        }

        var currentModel: ChatMessage? = null
        try {
            var step = 0
            val maxSteps = settings.current.maxSteps
            while (step < maxSteps) {
                step++
                val modelMessage = ChatMessage(
                    role = Role.MODEL,
                    parts = mutableListOf(),
                    status = MessageStatus.STREAMING,
                    brain = "Gemini"
                )
                currentModel = modelMessage
                conversation.messages += modelMessage
                listener.onUpdate()

                // Connect mode may still be warming up. Never hide the reply
                // behind it: the thinking bubble is already on screen and a
                // missing tool list only means this turn answers without them.
                if (step == 1 && settings.current.hasComposioKey && composio.declarations().isEmpty()) {
                    withTimeoutOrNull(5_000) {
                        runCatching { composio.ensure() }
                    }
                }

                val calls = streamModel(conversation, modelMessage, listener)
                modelMessage.status = MessageStatus.DONE
                if (calls.isEmpty()) {
                    listener.onPersist()
                    return
                }
                executeCalls(conversation, calls, listener, interactive)
                listener.onPersist()
            }
            val last = conversation.messages.lastOrNull { it.role == Role.MODEL }
            if (last != null) {
                last.status = MessageStatus.ERROR
                last.error = "Stopped after $maxSteps tool steps. Send \"continue\" to keep going."
            }
            listener.onUpdate()
            listener.onPersist()
        } catch (cancel: CancellationException) {
            val message = currentModel
            if (message != null && message.status == MessageStatus.STREAMING) {
                message.status = MessageStatus.STOPPED
            }
            throw cancel
        } catch (error: Exception) {
            val message = currentModel
            // Lost the network mid-chat: answer on-device instead of failing,
            // as long as nothing was streamed and no tool has run in this turn.
            val local = models.active()
            val lastUser = conversation.messages.indexOfLast { it.role == Role.USER }
            val firstStep = message != null && conversation.messages.indexOf(message) == lastUser + 1
            if (settings.current.brainMode == BrainMode.AUTO && firstStep &&
                message != null && message.parts.isEmpty() && isNetworkError(error)
            ) {
                conversation.messages.remove(message)
                listener.onUpdate()
                if (answerBudgetOnDevice(conversation, listener)) return
                if (local != null) {
                    runLocal(conversation, local, listener, note = "Gemini was unreachable, so I answered on-device.")
                    return
                }
                conversation.messages += message
            }
            if (message != null) {
                message.status = MessageStatus.ERROR
                message.error = friendly(error)
            }
            listener.onUpdate()
            listener.onPersist()
        }
    }

    /** One offline reply: no tools, streamed from the on-device model. */
    private suspend fun runLocal(
        conversation: Conversation,
        model: InstalledModel,
        listener: Listener,
        note: String?
    ) {
        val message = ChatMessage(
            role = Role.MODEL,
            parts = mutableListOf(),
            status = MessageStatus.STREAMING,
            brain = "On-device · ${model.name}"
        )
        conversation.messages += message
        listener.onUpdate()
        try {
            if (localLlm.loadedName != model.name) {
                // First reply after launch loads the weights; this can take a few seconds.
                localLlm.ensureLoaded(model)
            }
            val prompt = buildOfflinePrompt(
                conversation = conversation,
                model = model,
                memory = memory.facts.value,
                countTokens = { localLlm.countTokens(model, it) },
                extraContext = budgetAssistant.snapshotForPrompt()
            )
            var lastUpdate = 0L
            val reply = localLlm.generate(model, prompt) { token ->
                val last = message.parts.lastOrNull()
                if (last is Part.Text) {
                    message.parts[message.parts.lastIndex] = last.copy(text = last.text + token)
                } else {
                    message.parts += Part.Text(token.trimStart())
                }
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 40) {
                    lastUpdate = now
                    listener.onUpdate()
                }
            }
            val clean = cleanOfflineReply(reply)
            message.parts.clear()
            message.parts += Part.Text(
                if (note != null) "$clean\n\n_${note}_" else clean.ifBlank { "…" }
            )
            message.status = MessageStatus.DONE
        } catch (cancel: CancellationException) {
            message.status = MessageStatus.STOPPED
            throw cancel
        } catch (error: Throwable) {
            message.status = MessageStatus.ERROR
            message.error = "The offline model failed: ${error.message ?: error.javaClass.simpleName}"
        }
        listener.onUpdate()
        listener.onPersist()
    }

    /** Answers a budget command without any model. Returns false if [conversation]'s last turn isn't one. */
    private suspend fun answerBudgetOnDevice(conversation: Conversation, listener: Listener): Boolean {
        val last = conversation.messages.lastOrNull { it.role == Role.USER } ?: return false
        if (last.parts.any { it !is Part.Text }) return false
        val reply = runCatching { budgetAssistant.reply(last.plainText()) }.getOrNull() ?: return false
        conversation.messages += ChatMessage(
            role = Role.MODEL,
            parts = mutableListOf(Part.Text(reply)),
            status = MessageStatus.DONE,
            brain = "On-device · budget"
        )
        listener.onUpdate()
        listener.onPersist()
        return true
    }

    private fun isNetworkError(error: Exception): Boolean =
        error !is ApiException && (error is IOException || !network.isOnline())

    private suspend fun streamModel(
        conversation: Conversation,
        modelMessage: ChatMessage,
        listener: Listener
    ): List<Part.ToolCall> {
        val calls = mutableListOf<Part.ToolCall>()
        var finishReason: String? = null
        var lastUpdate = 0L
        var visibleText = false

        fun tick(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastUpdate > 40) {
                lastUpdate = now
                listener.onUpdate()
            }
        }

        fun appendText(value: String, thought: Boolean) {
            if (value.isEmpty() || thought) return
            visibleText = true
            val last = modelMessage.parts.lastOrNull()
            if (last is Part.Text && !last.thought) {
                modelMessage.parts[modelMessage.parts.lastIndex] = last.copy(text = last.text + value)
            } else {
                modelMessage.parts += Part.Text(value)
            }
            tick()
        }

        val tools = (nativeTools.declarations() + composio.declarations())
            .distinctBy { it.name }
            .map { tool ->
                GeminiFunctionDeclaration(
                    name = tool.name,
                    description = tool.description.take(2000),
                    parameters = runCatching { SchemaSanitizer.sanitize(tool.inputSchema) }
                        .getOrElse { buildJsonObject { } }
                )
            }

        val history = conversation.messages.dropLast(1)
        val request = GeminiRequest(
            contents = buildContents(history),
            systemInstruction = GeminiContent(
                parts = listOf(
                    GeminiPart(
                        text = buildSystemPrompt(settings.current, composio.status.value, memory.facts.value)
                    )
                )
            ),
            tools = if (tools.isEmpty()) null else listOf(GeminiTool(tools)),
            generationConfig = GeminiGenerationConfig(temperature = 0.7, maxOutputTokens = 8192)
        )

        gemini.stream(request) { chunk ->
            chunk.promptFeedback?.blockReason?.let { reason ->
                throw ApiException(400, "Gemini blocked this request ($reason).")
            }
            for (candidate in chunk.candidates) {
                candidate.finishReason?.let { finishReason = it }
                val parts = candidate.content?.parts ?: continue
                for (part in parts) {
                    val functionCall = part.functionCall
                    if (functionCall != null) {
                        if (functionCall.name.isBlank()) continue
                        val id = functionCall.id ?: UUID.randomUUID().toString()
                        if (calls.none { it.id == id }) {
                            val call = Part.ToolCall(
                                id = id,
                                name = functionCall.name,
                                argsJson = (functionCall.args ?: buildJsonObject { }).toString(),
                                thoughtSignature = part.thoughtSignature
                            )
                            calls += call
                            modelMessage.parts += call
                            tick()
                        }
                    } else if (part.text != null) {
                        appendText(part.text, part.thought == true)
                    }
                }
            }
        }
        tick(force = true)

        when (finishReason) {
            "MALFORMED_FUNCTION_CALL" -> throw ApiException(
                502,
                "Gemini produced a malformed tool call. Try rephrasing."
            )
            "SAFETY", "RECITATION", "PROHIBITED_CONTENT" ->
                throw ApiException(400, "Gemini stopped this reply ($finishReason).")
        }
        if (calls.isEmpty() && !visibleText && modelMessage.error == null) {
            throw ApiException(502, "Gemini returned an empty reply. Try again.")
        }
        return calls
    }

    private suspend fun executeCalls(
        conversation: Conversation,
        calls: List<Part.ToolCall>,
        listener: Listener,
        interactive: Boolean
    ) {
        for (call in calls) {
            val args = parseObject(call.argsJson)
            val native = nativeTools.declarations().firstOrNull { it.name == call.name }
            val remote = composio.declarations().firstOrNull { it.name == call.name }

            if (native == null && remote == null) {
                finishCall(
                    conversation, call,
                    ok = false,
                    result = errorJson("Unknown tool \"${call.name}\". Use only the provided tools."),
                    displayError = "Unknown tool",
                    listener = listener
                )
                continue
            }

            val needsApproval = remote != null && composio.needsApproval(call.name, args)
            if (needsApproval && settings.current.approvalMode == ApprovalMode.MANUAL && !interactive) {
                finishCall(
                    conversation, call,
                    ok = false,
                    result = errorJson("This action needs the user's approval and they are not in the app right now. Do not retry it; mention it in your summary instead."),
                    displayError = "Skipped: needs your approval",
                    listener = listener
                )
                continue
            }
            if (needsApproval && settings.current.approvalMode == ApprovalMode.MANUAL) {
                call.status = ToolStatus.WAITING_APPROVAL
                listener.onUpdate()
                val allowed = approvals.request(
                    callId = call.id,
                    toolName = composio.prettyName(call.name),
                    summary = prettyArgs(call.argsJson)
                )
                if (!allowed) {
                    finishCall(
                        conversation, call,
                        ok = false,
                        result = errorJson("The user denied this action."),
                        displayError = "Denied by you",
                        listener = listener
                    )
                    continue
                }
            }

            call.status = ToolStatus.RUNNING
            listener.onUpdate()
            var ok = true
            val result = try {
                if (native != null) nativeTools.execute(call.name, args)
                else composio.execute(call.name, args)
            } catch (cancel: CancellationException) {
                call.status = ToolStatus.ERROR
                call.error = "Stopped"
                throw cancel
            } catch (error: Exception) {
                ok = false
                errorJson(friendly(error))
            }
            finishCall(
                conversation, call,
                ok = ok,
                result = result,
                displayError = if (ok) null else result,
                listener = listener
            )
        }
    }

    private fun finishCall(
        conversation: Conversation,
        call: Part.ToolCall,
        ok: Boolean,
        result: String,
        displayError: String?,
        listener: Listener
    ) {
        val clipped = clip(result, MAX_TOOL_CHARS)
        call.status = if (ok) ToolStatus.OK else ToolStatus.ERROR
        call.resultJson = clipped
        call.error = displayError
        conversation.messages += ChatMessage(
            role = Role.TOOL,
            parts = mutableListOf(Part.ToolResponse(call.id, call.name, clipped, ok))
        )
        listener.onUpdate()
    }

    private fun buildContents(messages: List<ChatMessage>): List<GeminiContent> {
        val contents = mutableListOf<GeminiContent>()
        for (message in messages) {
            val parts = mutableListOf<GeminiPart>()
            if (message.role == Role.TOOL) {
                for (part in message.parts.filterIsInstance<Part.ToolResponse>()) {
                    parts += GeminiPart(
                        functionResponse = GeminiFunctionResponse(
                            id = part.callId,
                            name = part.name,
                            response = responseObject(part.responseJson)
                        )
                    )
                }
            } else {
                for (part in message.parts) {
                    when (part) {
                        is Part.Text -> if (!part.thought) parts += GeminiPart(text = part.text)
                        is Part.Image -> parts += imagePart(part)
                        is Part.File -> parts += filePart(part)
                        is Part.ToolCall -> parts += GeminiPart(
                            functionCall = GeminiFunctionCall(
                                id = part.id,
                                name = part.name,
                                args = parseObject(part.argsJson)
                            ),
                            thoughtSignature = part.thoughtSignature
                        )
                        is Part.ToolResponse -> Unit
                    }
                }
            }
            if (parts.isEmpty()) continue
            val role = if (message.role == Role.MODEL) "model" else "user"
            val last = contents.lastOrNull()
            if (last != null && last.role == role) {
                contents[contents.lastIndex] = last.copy(parts = last.parts + parts)
            } else {
                contents += GeminiContent(role = role, parts = parts)
            }
        }
        return contents
    }

    private fun imagePart(part: Part.Image): GeminiPart {
        val file = File(part.path)
        if (!file.exists()) {
            return GeminiPart(text = "[Image \"${part.name}\" is no longer available.]")
        }
        if (file.length() > MAX_INLINE_BYTES) {
            return GeminiPart(text = "[Image \"${part.name}\" is too large to attach (${file.length()} bytes).]")
        }
        val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        return GeminiPart(
            inlineData = GeminiInlineData(
                mimeType = part.mime.ifBlank { "image/jpeg" },
                data = data
            )
        )
    }

    private fun filePart(part: Part.File): GeminiPart {
        val file = File(part.path)
        if (!file.exists()) {
            return GeminiPart(text = "[File \"${part.name}\" is no longer available.]")
        }
        val mime = part.mime.lowercase()
        val textLike = mime.startsWith("text/") ||
            mime in setOf(
                "application/json", "application/xml", "application/javascript",
                "application/x-yaml", "application/yaml", "application/x-sh"
            )
        if (textLike) {
            val content = runCatching { file.readText().take(MAX_TEXT_CHARS) }.getOrElse {
                return GeminiPart(text = "[Could not read \"${part.name}\".]")
            }
            return GeminiPart(text = "Attached file \"${part.name}\":\n\n$content")
        }
        val inlineable = mime.startsWith("image/") ||
            mime == "application/pdf" ||
            mime.startsWith("audio/") ||
            mime.startsWith("video/")
        if (inlineable && file.length() <= MAX_INLINE_BYTES) {
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            return GeminiPart(inlineData = GeminiInlineData(mimeType = mime, data = data))
        }
        return GeminiPart(
            text = "[Attached file \"${part.name}\" (${mime.ifBlank { "unknown type" }}, ${file.length()} bytes) is saved on the phone but cannot be read inline.]"
        )
    }

    private fun parseObject(json: String): JsonObject {
        val element = runCatching { Http.json.parseToJsonElement(json) }.getOrNull()
        return element as? JsonObject ?: buildJsonObject { }
    }

    private fun responseObject(json: String): JsonObject {
        val element = runCatching { Http.json.parseToJsonElement(json) }.getOrNull()
        return when (element) {
            is JsonObject -> element
            null -> buildJsonObject { put("result", json.take(MAX_TEXT_CHARS)) }
            else -> buildJsonObject { put("result", element) }
        }
    }

    private fun prettyArgs(argsJson: String): String {
        val element = runCatching { Http.json.parseToJsonElement(argsJson) }.getOrNull()
            ?: return clip(argsJson, 1200)
        return clip(element.toString(), 1200)
    }

    private fun errorJson(message: String): String =
        buildJsonObject { put("error", message.take(2000)) }.toString()

    private fun clip(value: String, limit: Int): String =
        if (value.length <= limit) value else value.take(limit) + "\n…[clipped]"

    private fun friendly(error: Exception): String = when (error) {
        is ApiException -> error.message
        else -> error.message ?: error.javaClass.simpleName
    }

    private companion object {
        const val MAX_TOOL_CHARS = 24_000
        const val MAX_TEXT_CHARS = 100_000
        const val MAX_INLINE_BYTES = 12L * 1024 * 1024
    }
}

sealed interface Brain {
    data object Gemini : Brain
    data class Local(val model: InstalledModel) : Brain
    data class Missing(val reason: String) : Brain
}
