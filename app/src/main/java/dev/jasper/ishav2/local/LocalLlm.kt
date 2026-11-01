package dev.jasper.ishav2.local

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Runs a downloaded `.task` model with MediaPipe's LLM Inference. The engine
 * (weights in memory) stays loaded between replies; each reply gets a fresh
 * session so a cancelled or overflowing turn can never poison the next one.
 */
class LocalLlm(private val context: Context) {

    private val mutex = Mutex()
    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    @Volatile
    var loadedName: String? = null
        private set

    /**
     * Streams a reply for [prompt]. [onToken] receives each new piece of text
     * as it is produced; the full reply is returned at the end.
     */
    suspend fun generate(
        model: InstalledModel,
        prompt: String,
        onToken: (String) -> Unit
    ): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            val llm = load(model)
            val session = LlmInferenceSession.createFromOptions(
                llm,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.95f)
                    .setTemperature(0.7f)
                    .build()
            )
            var future: com.google.common.util.concurrent.ListenableFuture<String>? = null
            try {
                session.addQueryChunk(prompt)
                suspendCancellableCoroutine { cont ->
                    val text = StringBuilder()
                    val started = session.generateResponseAsync { partial, done ->
                        if (!partial.isNullOrEmpty()) {
                            text.append(partial)
                            onToken(partial)
                        }
                        if (done && cont.isActive) cont.resume(text.toString())
                    }
                    future = started
                    started.addListener({
                        val failure = runCatching { started.get() }.exceptionOrNull()
                        if (failure != null && cont.isActive) {
                            cont.resumeWithException(failure.cause ?: failure)
                        }
                    }, Runnable::run)
                    cont.invokeOnCancellation {
                        runCatching { session.cancelGenerateResponseAsync() }
                    }
                }
            } finally {
                // Let a cancelled generation wind down before freeing the session.
                runCatching { future?.get(3, TimeUnit.SECONDS) }
                runCatching { session.close() }
            }
        }
    }

    /** Token count with the loaded tokenizer, or a rough estimate before load. */
    fun countTokens(model: InstalledModel, text: String): Int {
        val llm = engine
        if (llm != null && loadedPath == model.file.absolutePath) {
            runCatching { return llm.sizeInTokens(text) }
        }
        return text.length / 3 + 1
    }

    suspend fun ensureLoaded(model: InstalledModel) = mutex.withLock {
        withContext(Dispatchers.Default) { load(model) }
    }

    fun unload() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
        loadedName = null
    }

    private fun load(model: InstalledModel): LlmInference {
        val path = model.file.absolutePath
        engine?.let { if (loadedPath == path) return it }
        unload()
        require(model.file.exists()) { "The offline model file is missing. Download it again." }
        Log.d(TAG, "loading ${model.name}")
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(model.maxTokens)
            .setMaxTopK(64)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()
        val created = LlmInference.createFromOptions(context, options)
        engine = created
        loadedPath = path
        loadedName = model.name
        return created
    }

    private companion object {
        const val TAG = "isha-local"
    }
}
