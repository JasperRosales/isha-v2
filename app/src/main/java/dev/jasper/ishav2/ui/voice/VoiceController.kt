package dev.jasper.ishav2.ui.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Voice mode for the chat: speech-to-text through the phone's speech recogniser
 * and text-to-speech for replies. All state is Compose state and every method
 * must be called on the main thread. Call [release] when the screen goes away.
 */
class VoiceController(private val context: Context) {

    enum class Phase { IDLE, LISTENING, SPEAKING }

    var phase by mutableStateOf(Phase.IDLE)
        private set

    /** What has been heard so far while listening. */
    var partial by mutableStateOf("")
        private set

    /** Microphone loudness, 0..1, for the listening animation. */
    var level by mutableFloatStateOf(0f)
        private set

    /** Id of the message being read aloud, if any. */
    var speakingId by mutableStateOf<String?>(null)
        private set

    val canListen: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeech: (() -> Unit)? = null
    private var pendingSpeechError: ((String) -> Unit)? = null

    /** Bumped on every new listen/speak so callbacks from an older one are ignored. */
    private var generation = 0
    private var onSpoken: (() -> Unit)? = null

    /**
     * Starts listening. [onResult] gets the final transcript. [onError] gets a
     * message to show, or null when nothing was said (not worth a message).
     */
    fun listen(onResult: (String) -> Unit, onError: (String?) -> Unit) {
        stopSpeaking()
        cancelListening()
        if (!canListen) {
            onError("This phone has no speech recognition service. Install or enable Google's speech services.")
            return
        }
        val gen = ++generation
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        var heardSpeech = false
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() {
                heardSpeech = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (gen != generation) return
                // Roughly -2..10 dB on most recognisers.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (gen == generation) level = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (gen != generation) return
                best(partialResults)?.let { partial = it }
            }

            override fun onResults(results: Bundle?) {
                if (gen != generation || phase != Phase.LISTENING) return
                val text = best(results)?.trim().orEmpty().ifEmpty { partial.trim() }
                finishListeningState()
                if (text.isNotEmpty()) onResult(text) else onError(null)
            }

            override fun onError(error: Int) {
                if (gen != generation || phase != Phase.LISTENING) return
                // Some recognisers end with an error even though partial text was heard.
                val heard = partial.trim()
                finishListeningState()
                if (heard.isNotEmpty() && (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_CLIENT)
                ) {
                    onResult(heard)
                    return
                }
                Log.d(TAG, "recogniser error $error (heard speech: $heardSpeech)")
                onError(errorMessage(error))
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        partial = ""
        level = 0f
        phase = Phase.LISTENING
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Give the user a moment to think between phrases.
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        runCatching { rec.startListening(intent) }.onFailure {
            finishListeningState()
            onError("Could not start the microphone.")
        }
    }

    /** Stop recording now and deliver what was heard. */
    fun finishListening() {
        if (phase == Phase.LISTENING) runCatching { recognizer?.stopListening() }
    }

    /** Stop recording and throw away what was heard. */
    fun cancelListening() {
        if (phase != Phase.LISTENING) return
        generation++
        runCatching { recognizer?.cancel() }
        finishListeningState()
    }

    /**
     * Reads [text] (already speakable) aloud. [onDone] runs only if it finishes
     * uninterrupted; [onError] runs if the phone has no working text-to-speech.
     */
    fun speak(id: String, text: String, onError: (String) -> Unit = {}, onDone: (() -> Unit)? = null) {
        cancelListening()
        stopSpeaking()
        if (text.isBlank()) return
        if (ttsFailed) {
            // Try again: the engine may have been installed or enabled since.
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
        }
        val gen = ++generation
        val engine = tts ?: createTts()
        speakingId = id
        phase = Phase.SPEAKING
        onSpoken = onDone
        val start = start@{
            if (gen != generation || ttsFailed) return@start
            val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
                .coerceAtMost(3000)
            val chunks = speechChunks(text, max)
            chunks.forEachIndexed { i, chunk ->
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val utterance = if (i == chunks.lastIndex) "$gen:last" else "$gen:$i"
                engine.speak(chunk, mode, null, utterance)
            }
        }
        if (ttsReady) {
            start()
        } else {
            pendingSpeech = start
            pendingSpeechError = onError
        }
    }

    fun stopSpeaking() {
        pendingSpeech = null
        pendingSpeechError = null
        onSpoken = null
        if (phase == Phase.SPEAKING) {
            generation++
            runCatching { tts?.stop() }
            phase = Phase.IDLE
            speakingId = null
        }
    }

    fun stopAll() {
        cancelListening()
        stopSpeaking()
    }

    fun release() {
        stopAll()
        runCatching { recognizer?.destroy() }
        recognizer = null
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
    }

    private fun createTts(): TextToSpeech {
        ttsFailed = false
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            main.post {
                if (tts !== engine) return@post
                if (status != TextToSpeech.SUCCESS) {
                    ttsFailed = true
                    Log.w(TAG, "text-to-speech unavailable ($status)")
                    val report = pendingSpeechError
                    stopSpeaking()
                    report?.invoke("Text-to-speech isn't available. Install or enable a voice in the phone's text-to-speech settings.")
                    return@post
                }
                val locale = Locale.getDefault()
                if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) engine.language = locale
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        main.post { utteranceEnded(utteranceId, completed = true) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { utteranceEnded(utteranceId, completed = false) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        main.post { utteranceEnded(utteranceId, completed = false) }
                    }
                })
                ttsReady = true
                val start = pendingSpeech
                pendingSpeech = null
                pendingSpeechError = null
                start?.invoke()
            }
        }
        tts = engine
        return engine
    }

    private fun utteranceEnded(utteranceId: String?, completed: Boolean) {
        val id = utteranceId ?: return
        val gen = id.substringBefore(':').toIntOrNull() ?: return
        if (gen != generation || phase != Phase.SPEAKING) return
        if (completed && !id.endsWith(":last")) return
        val done = onSpoken
        onSpoken = null
        phase = Phase.IDLE
        speakingId = null
        if (completed) done?.invoke()
    }

    private fun finishListeningState() {
        phase = Phase.IDLE
        level = 0f
        partial = ""
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }

    private fun errorMessage(error: Int): String? = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
        SpeechRecognizer.ERROR_CLIENT -> null
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "isha needs microphone access for voice mode."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            "Speech recognition needs a connection here. Download your language for offline speech in the phone's voice-typing settings."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "Speech recognition doesn't support your phone's language yet."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The microphone is busy. Try again in a moment."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many voice requests. Try again in a moment."
        SpeechRecognizer.ERROR_AUDIO -> "Could not record audio."
        else -> "Voice input stopped (error $error)."
    }

    private companion object {
        const val TAG = "IshaVoice"
    }
}
