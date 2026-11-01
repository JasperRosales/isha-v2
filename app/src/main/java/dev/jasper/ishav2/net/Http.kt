package dev.jasper.ishav2.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * @param retryAfterMs how long the provider asked us to back off, when known
 * (e.g. Gemini's free-tier quota). Used to route around a rate-limited brain.
 */
class ApiException(
    val code: Int,
    override val message: String,
    val retryAfterMs: Long? = null
) : IOException(message)

object Http {
    val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun body(element: JsonElement) = element.toString().toRequestBody(mediaTypeJson)

    fun errorMessage(body: String?): String {
        if (body.isNullOrBlank()) return "The server returned an empty error."
        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject
        }.getOrNull()
        val direct = parsed?.get("error")
        if (direct is JsonElement) {
            val obj = runCatching { direct.jsonObject }.getOrNull()
            val msg = obj?.get("message")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            if (!msg.isNullOrBlank()) return msg
        }
        val message = parsed?.get("message")?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (!message.isNullOrBlank()) return message
        return body.take(500)
    }
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.close()
            } else {
                continuation.resume(response)
            }
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
