package dev.jasper.ishav2.data

import android.content.Context
import dev.jasper.ishav2.util.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ApprovalMode { MANUAL, AUTO }

enum class ComposioKeyMode { NONE, CONNECT, PROJECT, UNKNOWN }

/**
 * Where replies come from. AUTO uses Groq (then Gemini) while the phone is online and
 * falls back to the downloaded on-device model when it is not.
 */
enum class BrainMode { AUTO, ONLINE, OFFLINE }

/** The cloud brains, tried in the order the user sets in Settings. */
enum class CloudBrain { GROQ, GEMINI, EXTRA }

/** Presets for the extra brain: any OpenAI-compatible Chat Completions API. */
enum class ExtraProvider(
    val label: String,
    val baseUrl: String,
    val keyUrl: String,
    val keyHint: String,
    val defaultModel: String,
    /** Groq/Cerebras accept max_completion_tokens; most others only max_tokens. */
    val tokensField: String
) {
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1", "https://cloud.cerebras.ai/", "csk-…", "gpt-oss-120b", "max_completion_tokens"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "https://openrouter.ai/settings/keys", "sk-or-…", "openai/gpt-oss-120b:free", "max_tokens"),
    CUSTOM("Custom", "", "", "API key", "", "max_tokens")
}

data class AppSettings(
    val geminiKey: String = "",
    val composioKey: String = "",
    val model: String = DEFAULT_MODEL,
    /** Main brain. Gemini takes over when Groq's free limits run out or Groq fails. */
    val groqKey: String = "",
    val groqModel: String = DEFAULT_GROQ_MODEL,
    val approvalMode: ApprovalMode = ApprovalMode.MANUAL,
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val userId: String = "",
    val lastConversationId: String? = null,
    val composioSessionId: String? = null,
    val onboarded: Boolean = false,
    val darkTheme: Boolean = true,
    val gmailPollingEnabled: Boolean = false,
    val gmailLastCheckedAt: Long = 0L,
    val gmailLastMessageId: String? = null,
    val classroomPollingEnabled: Boolean = false,
    val classroomLastCheckedAt: Long = 0L,
    val classroomLastActivityId: String? = null,
    val brainMode: BrainMode = BrainMode.AUTO,
    val localModelId: String? = null,
    val huggingFaceToken: String = "",
    val autoApproveReads: Boolean = true,
    val briefingEnabled: Boolean = false,
    val briefingHour: Int = 7,
    val briefingMinute: Int = 30,
    val hideTransactions: Boolean = false,
    /** Speak isha's reply aloud when the message was spoken with the mic. */
    val voiceReadReplies: Boolean = true,
    /** After a spoken reply, listen again so the conversation can continue hands-free. */
    val voiceHandsFree: Boolean = true,
    /** Cloud brains in the order they are tried. Always holds every [CloudBrain] once. */
    val brainOrder: List<CloudBrain> = DEFAULT_BRAIN_ORDER,
    /** Third brain on an OpenAI-compatible API (Cerebras, OpenRouter, …). */
    val extraProvider: ExtraProvider = ExtraProvider.CEREBRAS,
    val extraKey: String = "",
    val extraModel: String = ExtraProvider.CEREBRAS.defaultModel,
    /** Only used with [ExtraProvider.CUSTOM]. */
    val extraBaseUrl: String = "",
    /**
     * Switch to the next brain when a reply hasn't started after this many
     * seconds (Gemini sometimes stalls). 0 turns it off.
     */
    val slowSwitchSeconds: Int = DEFAULT_SLOW_SWITCH_SECONDS
) {
    val hasGeminiKey: Boolean get() = geminiKey.isNotBlank()
    val hasGroqKey: Boolean get() = groqKey.isNotBlank()
    val extraUrl: String
        get() = (if (extraProvider == ExtraProvider.CUSTOM) extraBaseUrl else extraProvider.baseUrl).trim().trimEnd('/')
    val hasExtraKey: Boolean get() = extraKey.isNotBlank() && extraUrl.isNotBlank() && extraModel.isNotBlank()

    fun hasKey(brain: CloudBrain): Boolean = when (brain) {
        CloudBrain.GROQ -> hasGroqKey
        CloudBrain.GEMINI -> hasGeminiKey
        CloudBrain.EXTRA -> hasExtraKey
    }

    fun brainLabel(brain: CloudBrain): String = when (brain) {
        CloudBrain.GROQ -> "Groq"
        CloudBrain.GEMINI -> "Gemini"
        CloudBrain.EXTRA -> extraProvider.label
    }

    /** Any cloud brain is configured. */
    val hasCloudKey: Boolean get() = CloudBrain.entries.any { hasKey(it) }
    val hasLocalModel: Boolean get() = !localModelId.isNullOrBlank()

    /** True when the app can answer at all: a cloud key or an on-device model. */
    val canChat: Boolean get() = hasCloudKey || hasLocalModel
    val hasComposioKey: Boolean get() = composioKey.isNotBlank()

    val composioMode: ComposioKeyMode
        get() = when {
            composioKey.isBlank() -> ComposioKeyMode.NONE
            composioKey.startsWith("ck_") -> ComposioKeyMode.CONNECT
            composioKey.startsWith("ak_") -> ComposioKeyMode.PROJECT
            else -> ComposioKeyMode.UNKNOWN
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_MAX_STEPS = 20
        const val DEFAULT_SLOW_SWITCH_SECONDS = 30
        val SLOW_SWITCH_CHOICES = listOf(0, 15, 30, 60)
        val DEFAULT_BRAIN_ORDER = listOf(CloudBrain.GROQ, CloudBrain.GEMINI, CloudBrain.EXTRA)

        /** Keeps the saved order valid: known entries once each, missing ones appended. */
        fun normalizeOrder(order: List<CloudBrain>): List<CloudBrain> =
            (order + DEFAULT_BRAIN_ORDER).distinct()
        /** Batches several tool calls per turn: fewer requests against Groq's 8k tokens/min free limit. */
        const val DEFAULT_GROQ_MODEL = "qwen/qwen3.8-27b"
        val GROQ_MODEL_SUGGESTIONS = listOf(
            "qwen/qwen3.8-27b",
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b"
        )
        val MODEL_SUGGESTIONS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )
    }
}

class SettingsStore(context: Context) {
    private val secure = SecurePrefs(context)
    private val prefs = context.getSharedPreferences("isha_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun saveGeminiKey(value: String) {
        secure.put(KEY_GEMINI, value.trim())
        _settings.value = current.copy(geminiKey = value.trim())
    }

    fun saveComposioKey(value: String) {
        secure.put(KEY_COMPOSIO, value.trim())
        _settings.value = current.copy(composioKey = value.trim())
    }

    /** Blank falls back to the key built into the app (local.properties), if any. */
    fun saveGroqKey(value: String) {
        secure.put(KEY_GROQ, value.trim())
        _settings.value = current.copy(groqKey = value.trim().ifBlank { BUILT_IN_GROQ_KEY })
    }

    fun setGroqModel(value: String) {
        val model = value.trim().ifBlank { AppSettings.DEFAULT_GROQ_MODEL }
        prefs.edit().putString(KEY_GROQ_MODEL, model).apply()
        _settings.value = current.copy(groqModel = model)
    }

    fun setBrainOrder(order: List<CloudBrain>) {
        val value = AppSettings.normalizeOrder(order)
        prefs.edit().putString(KEY_BRAIN_ORDER, value.joinToString(",") { it.name }).apply()
        _settings.value = current.copy(brainOrder = value)
    }

    fun saveExtraKey(value: String) {
        secure.put(KEY_EXTRA, value.trim())
        _settings.value = current.copy(extraKey = value.trim())
    }

    /** Switching provider also resets the model to that provider's default. */
    fun setExtraProvider(provider: ExtraProvider) {
        val model = if (provider == current.extraProvider) current.extraModel else provider.defaultModel
        prefs.edit()
            .putString(KEY_EXTRA_PROVIDER, provider.name)
            .putString(KEY_EXTRA_MODEL, model)
            .apply()
        _settings.value = current.copy(extraProvider = provider, extraModel = model)
    }

    fun setExtraModel(value: String) {
        val model = value.trim()
        prefs.edit().putString(KEY_EXTRA_MODEL, model).apply()
        _settings.value = current.copy(extraModel = model)
    }

    fun setExtraBaseUrl(value: String) {
        val url = value.trim()
        prefs.edit().putString(KEY_EXTRA_URL, url).apply()
        _settings.value = current.copy(extraBaseUrl = url)
    }

    fun setSlowSwitchSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, 300)
        prefs.edit().putInt(KEY_SLOW_SWITCH, value).apply()
        _settings.value = current.copy(slowSwitchSeconds = value)
    }

    fun setModel(value: String) {
        val model = value.trim().ifBlank { AppSettings.DEFAULT_MODEL }
        prefs.edit().putString(KEY_MODEL, model).apply()
        _settings.value = current.copy(model = model)
    }

    fun setApprovalMode(mode: ApprovalMode) {
        prefs.edit().putString(KEY_APPROVAL, mode.name).apply()
        _settings.value = current.copy(approvalMode = mode)
    }

    fun setMaxSteps(steps: Int) {
        val value = steps.coerceIn(4, 40)
        prefs.edit().putInt(KEY_MAX_STEPS, value).apply()
        _settings.value = current.copy(maxSteps = value)
    }

    fun setLastConversation(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_LAST_CHAT) else putString(KEY_LAST_CHAT, id)
        }.apply()
        _settings.value = current.copy(lastConversationId = id)
    }

    fun setComposioSession(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_SESSION) else putString(KEY_SESSION, id)
        }.apply()
        _settings.value = current.copy(composioSessionId = id)
    }

    fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        _settings.value = current.copy(onboarded = value)
    }

    fun setHideTransactions(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_TX, hidden).apply()
        _settings.value = current.copy(hideTransactions = hidden)
    }

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _settings.value = current.copy(darkTheme = enabled)
    }

    fun setGmailPolling(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GMAIL_POLL, enabled).apply()
        _settings.value = current.copy(gmailPollingEnabled = enabled)
    }

    fun setGmailLastChecked(at: Long, messageId: String?) {
        prefs.edit()
            .putLong(KEY_GMAIL_LAST_AT, at)
            .apply { if (messageId == null) remove(KEY_GMAIL_LAST_ID) else putString(KEY_GMAIL_LAST_ID, messageId) }
            .apply()
        _settings.value = current.copy(gmailLastCheckedAt = at, gmailLastMessageId = messageId)
    }

    fun setClassroomPolling(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLASS_POLL, enabled).apply()
        _settings.value = current.copy(classroomPollingEnabled = enabled)
    }

    fun setClassroomLastChecked(at: Long, activityId: String?) {
        prefs.edit()
            .putLong(KEY_CLASS_LAST_AT, at)
            .apply { if (activityId == null) remove(KEY_CLASS_LAST_ID) else putString(KEY_CLASS_LAST_ID, activityId) }
            .apply()
        _settings.value = current.copy(classroomLastCheckedAt = at, classroomLastActivityId = activityId)
    }

    fun setBrainMode(mode: BrainMode) {
        prefs.edit().putString(KEY_BRAIN, mode.name).apply()
        _settings.value = current.copy(brainMode = mode)
    }

    fun setLocalModel(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_LOCAL_MODEL) else putString(KEY_LOCAL_MODEL, id)
        }.apply()
        _settings.value = current.copy(localModelId = id)
    }

    fun saveHuggingFaceToken(value: String) {
        secure.put(KEY_HF_TOKEN, value.trim())
        _settings.value = current.copy(huggingFaceToken = value.trim())
    }

    fun setAutoApproveReads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_READS, enabled).apply()
        _settings.value = current.copy(autoApproveReads = enabled)
    }

    fun setVoice(readReplies: Boolean = current.voiceReadReplies, handsFree: Boolean = current.voiceHandsFree) {
        prefs.edit()
            .putBoolean(KEY_VOICE_READ, readReplies)
            .putBoolean(KEY_VOICE_HANDS_FREE, handsFree)
            .apply()
        _settings.value = current.copy(voiceReadReplies = readReplies, voiceHandsFree = handsFree)
    }

    fun setBriefing(enabled: Boolean, hour: Int = current.briefingHour, minute: Int = current.briefingMinute) {
        prefs.edit()
            .putBoolean(KEY_BRIEFING, enabled)
            .putInt(KEY_BRIEFING_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_BRIEFING_MINUTE, minute.coerceIn(0, 59))
            .apply()
        _settings.value = current.copy(
            briefingEnabled = enabled,
            briefingHour = hour.coerceIn(0, 23),
            briefingMinute = minute.coerceIn(0, 59)
        )
    }

    fun clearChatPreferences() {
        prefs.edit().remove(KEY_LAST_CHAT).apply()
        _settings.value = current.copy(lastConversationId = null)
    }

    fun clearEverything() {
        secure.clear()
        prefs.edit().clear().apply()
        _settings.value = AppSettings(groqKey = BUILT_IN_GROQ_KEY, userId = newUserId(), onboarded = false)
        prefs.edit().putString(KEY_USER_ID, _settings.value.userId).apply()
    }

    private fun load(): AppSettings {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId.isNullOrBlank()) {
            userId = newUserId()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        val model = prefs.getString(KEY_MODEL, AppSettings.DEFAULT_MODEL)
            ?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_MODEL
        val approval = runCatching {
            ApprovalMode.valueOf(prefs.getString(KEY_APPROVAL, null) ?: "")
        }.getOrDefault(ApprovalMode.MANUAL)
        val extraProvider = ExtraProvider.entries
            .firstOrNull { it.name == prefs.getString(KEY_EXTRA_PROVIDER, null) } ?: ExtraProvider.CEREBRAS
        return AppSettings(
            geminiKey = secure.get(KEY_GEMINI).orEmpty(),
            composioKey = secure.get(KEY_COMPOSIO).orEmpty(),
            model = model,
            groqKey = secure.get(KEY_GROQ)?.takeIf { it.isNotBlank() } ?: BUILT_IN_GROQ_KEY,
            groqModel = prefs.getString(KEY_GROQ_MODEL, null)
                ?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_GROQ_MODEL,
            approvalMode = approval,
            maxSteps = prefs.getInt(KEY_MAX_STEPS, AppSettings.DEFAULT_MAX_STEPS),
            userId = userId,
            lastConversationId = prefs.getString(KEY_LAST_CHAT, null),
            composioSessionId = prefs.getString(KEY_SESSION, null),
            onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
            gmailPollingEnabled = prefs.getBoolean(KEY_GMAIL_POLL, false),
            gmailLastCheckedAt = prefs.getLong(KEY_GMAIL_LAST_AT, 0L),
            gmailLastMessageId = prefs.getString(KEY_GMAIL_LAST_ID, null),
            classroomPollingEnabled = prefs.getBoolean(KEY_CLASS_POLL, false),
            classroomLastCheckedAt = prefs.getLong(KEY_CLASS_LAST_AT, 0L),
            classroomLastActivityId = prefs.getString(KEY_CLASS_LAST_ID, null),
            brainMode = runCatching {
                BrainMode.valueOf(prefs.getString(KEY_BRAIN, null) ?: "")
            }.getOrDefault(BrainMode.AUTO),
            localModelId = prefs.getString(KEY_LOCAL_MODEL, null),
            huggingFaceToken = secure.get(KEY_HF_TOKEN).orEmpty(),
            autoApproveReads = prefs.getBoolean(KEY_AUTO_READS, true),
            briefingEnabled = prefs.getBoolean(KEY_BRIEFING, false),
            briefingHour = prefs.getInt(KEY_BRIEFING_HOUR, 7),
            briefingMinute = prefs.getInt(KEY_BRIEFING_MINUTE, 30),
            hideTransactions = prefs.getBoolean(KEY_HIDE_TX, false),
            voiceReadReplies = prefs.getBoolean(KEY_VOICE_READ, true),
            voiceHandsFree = prefs.getBoolean(KEY_VOICE_HANDS_FREE, true),
            brainOrder = AppSettings.normalizeOrder(
                prefs.getString(KEY_BRAIN_ORDER, null).orEmpty().split(',')
                    .mapNotNull { name -> CloudBrain.entries.firstOrNull { it.name == name.trim() } }
            ),
            extraProvider = extraProvider,
            extraKey = secure.get(KEY_EXTRA).orEmpty(),
            extraModel = prefs.getString(KEY_EXTRA_MODEL, null) ?: extraProvider.defaultModel,
            extraBaseUrl = prefs.getString(KEY_EXTRA_URL, null).orEmpty(),
            slowSwitchSeconds = prefs.getInt(KEY_SLOW_SWITCH, AppSettings.DEFAULT_SLOW_SWITCH_SECONDS)
        )
    }

    private fun newUserId(): String = "isha-" + UUID.randomUUID().toString().take(8)

    private companion object {
        const val KEY_GEMINI = "gemini_key"
        const val KEY_COMPOSIO = "composio_key"
        const val KEY_MODEL = "model"
        const val KEY_GROQ = "groq_key"
        const val KEY_GROQ_MODEL = "groq_model"
        /** Optional key baked in at build time from local.properties (groq.api.key). */
        val BUILT_IN_GROQ_KEY: String = dev.jasper.ishav2.BuildConfig.GROQ_API_KEY
        const val KEY_APPROVAL = "approval_mode"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_USER_ID = "user_id"
        const val KEY_LAST_CHAT = "last_chat"
        const val KEY_SESSION = "composio_session"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_GMAIL_POLL = "gmail_poll_enabled"
        const val KEY_GMAIL_LAST_AT = "gmail_poll_last_at"
        const val KEY_GMAIL_LAST_ID = "gmail_poll_last_id"
        const val KEY_CLASS_POLL = "classroom_poll_enabled"
        const val KEY_CLASS_LAST_AT = "classroom_poll_last_at"
        const val KEY_CLASS_LAST_ID = "classroom_poll_last_id"
        const val KEY_BRAIN = "brain_mode"
        const val KEY_LOCAL_MODEL = "local_model"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_AUTO_READS = "auto_approve_reads"
        const val KEY_BRIEFING = "briefing_enabled"
        const val KEY_BRIEFING_HOUR = "briefing_hour"
        const val KEY_BRIEFING_MINUTE = "briefing_minute"
        const val KEY_HIDE_TX = "budget_hide_transactions"
        const val KEY_VOICE_READ = "voice_read_replies"
        const val KEY_VOICE_HANDS_FREE = "voice_hands_free"
        const val KEY_BRAIN_ORDER = "brain_order"
        const val KEY_EXTRA = "extra_key"
        const val KEY_EXTRA_PROVIDER = "extra_provider"
        const val KEY_EXTRA_MODEL = "extra_model"
        const val KEY_EXTRA_URL = "extra_base_url"
        const val KEY_SLOW_SWITCH = "slow_switch_seconds"
    }
}
