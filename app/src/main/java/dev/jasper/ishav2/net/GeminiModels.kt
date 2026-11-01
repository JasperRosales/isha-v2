package dev.jasper.ishav2.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    val thought: Boolean? = null,
    val thoughtSignature: String? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GeminiFunctionCall(
    val id: String? = null,
    val name: String,
    val args: JsonObject? = null
)

@Serializable
data class GeminiFunctionResponse(
    val id: String? = null,
    val name: String,
    val response: JsonObject
)

@Serializable
data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double? = 0.7,
    val maxOutputTokens: Int? = 8192,
    val thinkingConfig: GeminiThinkingConfig? = null
)

@Serializable
data class GeminiThinkingConfig(
    val thinkingBudget: Int? = null,
    val includeThoughts: Boolean? = null
)

@Serializable
data class GeminiChunk(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null,
    val blockReasonMessage: String? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String = "Gemini request failed",
    val status: String? = null
)

@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModelInfo> = emptyList()
)

@Serializable
data class GeminiModelInfo(
    val name: String = "",
    @SerialName("supportedGenerationMethods")
    val supportedMethods: List<String> = emptyList()
)
